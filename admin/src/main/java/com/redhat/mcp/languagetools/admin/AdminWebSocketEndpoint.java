package com.redhat.mcp.languagetools.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.mcp.languagetools.admin.dto.*;
import com.redhat.mcp.languagetools.admin.ws.*;
import com.redhat.mcp.languagetools.dap.session.DapSessionEvent;
import com.redhat.mcp.languagetools.dap.session.DapSessionManager;
import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.dap.trace.DapTraceMessage;
import com.redhat.mcp.languagetools.lsp.server.LspServerStatusChangeEvent;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceMessage;
import com.redhat.mcp.languagetools.mcp.trace.McpTrace;
import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.redhat.mcp.languagetools.progress.ProgressBroadcaster;
import com.redhat.mcp.languagetools.settings.Settings;
import com.redhat.mcp.languagetools.workspace.Workspace;
import com.redhat.mcp.languagetools.workspace.WorkspaceChangeEvent;
import com.redhat.mcp.languagetools.Application;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint for real-time admin UI updates.
 * Replaces SSE streams and polling with a single bidirectional connection.
 */
@ServerEndpoint("/api/admin/ws")
@ApplicationScoped
public class AdminWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(AdminWebSocketEndpoint.class);

    @Inject
    Application application;

    @Inject
    ConnectionManager connectionManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LspTraceCollector lspTraceCollector;

    @Inject
    McpTraceCollector mcpTraceCollector;

    @Inject
    DapTraceCollector dapTraceCollector;

    @Inject
    DapSessionManager dapSessionManager;

    @Inject
    AdminProgressBroadcaster progressBroadcaster;

    @Inject
    Settings settings;

    // Thread-safe set of active WebSocket sessions
    private final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        LOG.infof("WebSocket client connected: %s (total: %d)", session.getId(), sessions.size());

        // Send initial state snapshot
        sendInitialState(session);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessions.remove(session);
        LOG.infof("WebSocket client disconnected: %s, reason: %s (remaining: %d)",
                session.getId(), closeReason, sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOG.errorf(throwable, "WebSocket error for session: %s", session.getId());
        sessions.remove(session);
    }

    /**
     * Send initial state when client connects.
     */
    private void sendInitialState(Session session) {
        try {
            // Send current workspaces
            WorkspacesUpdateWsMessage workspacesMsg = new WorkspacesUpdateWsMessage(
                    getCurrentWorkspaces()
            );
            sendToSession(session, workspacesMsg);

            // Send current MCP clients
            McpClientsUpdateWsMessage clientsMsg = new McpClientsUpdateWsMessage(
                    getCurrentMcpClients()
            );
            sendToSession(session, clientsMsg);

            // Send trace levels early so the UI has them before trace history
            sendTraceLevels(session);

            // Send LSP trace history for all servers
            sendLspTraceHistory(session);

            // Send MCP trace history
            sendMcpTraceHistory(session);

            // Send DAP trace history
            sendDapTraceHistory(session);

            // Send active progress state
            sendProgressState(session);

            LOG.debugf("Initial state sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send initial state to session: %s", session.getId());
        }
    }

    /**
     * Send LSP trace history for all servers.
     */
    private void sendLspTraceHistory(Session session) {
        try {
            // Get all workspaces and their servers
            for (var workspace : application.getWorkspaces()) {
                for (var server : workspace.getLspServers()) {
                    // Get last 200 traces for this server
                    var traces = lspTraceCollector.getTracesForWorkspaceAndServer(
                        workspace.getNormalizedUri(),
                        server.getId(),
                        200
                    );

                    // Send each trace
                    for (var trace : traces) {
                        LspTraceWsMessage msg = new LspTraceWsMessage(
                            trace.workspaceUri(),
                            trace.serverId(),
                            trace.jsonContent(),
                            trace.messageType()
                        );
                        sendToSession(session, msg);
                    }
                }
            }
            LOG.debugf("LSP trace history sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send LSP trace history to session: %s", session.getId());
        }
    }

    /**
     * Send MCP trace history.
     */
    private void sendMcpTraceHistory(Session session) {
        try {
            // Get last 500 MCP traces
            var traces = mcpTraceCollector.getTraces(500);

            // Send each trace
            for (var trace : traces) {
                McpTraceWsMessage msg = new McpTraceWsMessage(
                    trace.connectionId(),
                    trace.message()
                );
                sendToSession(session, msg);
            }
            LOG.debugf("MCP trace history sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send MCP trace history to session: %s", session.getId());
        }
    }

    /**
     * Send DAP trace history for all sessions.
     */
    private void sendDapTraceHistory(Session session) {
        try {
            // Get all active DAP sessions
            var dapSessions = dapSessionManager.getAllSessions();
            LOG.infof("Sending DAP trace history: %d sessions", dapSessions.size());

            // Send traces for each session (last 200 traces per session)
            for (var dapSession : dapSessions) {
                var traces = dapTraceCollector.getTracesForSession(dapSession.getSessionId(), 200);
                LOG.infof("Session %s: sending %d traces", dapSession.getSessionId(), traces.size());

                // Send each trace
                for (var trace : traces) {
                    DapTraceWsMessage msg = new DapTraceWsMessage(
                        trace.workspaceUri(),
                        trace.sessionId(),
                        trace.jsonContent(),
                        trace.messageType()
                    );
                    sendToSession(session, msg);
                }
            }
            LOG.infof("DAP trace history sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send DAP trace history to session: %s", session.getId());
        }
    }

    /**
     * Send active progress state (init + last update) for tasks currently in progress.
     */
    private void sendProgressState(Session session) {
        try {
            for (AdminProgressBroadcaster.ActiveTask task : progressBroadcaster.getActiveTasks()) {
                if (task.getInitMessage() != null) {
                    sendToSession(session, task.getInitMessage());
                }
                if (task.getLastUpdate() != null) {
                    sendToSession(session, task.getLastUpdate());
                }
            }
            LOG.debugf("Progress state sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send progress state to session: %s", session.getId());
        }
    }

    /**
     * Send saved trace levels to a newly connected session.
     * Parses settings keys like "lsp.serverId.trace", "dap.serverId.trace", "mcp.trace".
     */
    private void sendTraceLevels(Session session) {
        try {
            for (var entry : settings.getTraceLevelEntries().entrySet()) {
                String key = entry.getKey();
                String traceLevel = entry.getValue();
                TraceLevelWsMessage msg = parseTraceLevelKey(key, traceLevel);
                if (msg != null) {
                    sendToSession(session, msg);
                }
            }
            LOG.debugf("Trace levels sent to session: %s", session.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send trace levels to session: %s", session.getId());
        }
    }

    private TraceLevelWsMessage parseTraceLevelKey(String key, String traceLevel) {
        // key format: "lsp.serverId.trace", "dap.serverId.trace", "mcp.trace"
        if (key.equals("mcp.trace")) {
            return new TraceLevelWsMessage("mcp", null, traceLevel);
        }
        if (key.startsWith("lsp.") && key.endsWith(".trace")) {
            String serverId = key.substring(4, key.length() - 6);
            return new TraceLevelWsMessage("lsp", serverId, traceLevel);
        }
        if (key.startsWith("dap.") && key.endsWith(".trace")) {
            String serverId = key.substring(4, key.length() - 6);
            return new TraceLevelWsMessage("dap", serverId, traceLevel);
        }
        return null;
    }

    /**
     * CDI observer for trace level changes — broadcasts to all clients.
     */
    void onTraceLevelUpdate(@Observes TraceLevelWsMessage msg) {
        broadcast(msg);
    }

    /**
     * CDI observer for LSP trace events.
     */
    void onLspTrace(@Observes LspTraceMessage trace) {
        LspTraceWsMessage msg = new LspTraceWsMessage(
                trace.workspaceUri(),
                trace.serverId(),
                trace.jsonContent(),
                trace.messageType()
        );
        broadcast(msg);
    }

    /**
     * CDI observer for MCP trace events.
     */
    void onMcpTrace(@Observes McpTrace trace) {
        McpTraceWsMessage msg = new McpTraceWsMessage(
                trace.connectionId(),
                trace.message()
        );
        broadcast(msg);
    }

    /**
     * CDI observer for DAP trace events.
     */
    void onDapTrace(@Observes DapTraceMessage trace) {
        var msg = new DapTraceWsMessage(
                trace.workspaceUri(),
                trace.sessionId(),
                trace.jsonContent(),
                trace.messageType()
        );
        broadcast(msg);
    }

    /**
     * CDI observer for DAP session events (created/changed/deleted).
     */
    void onDapSessionEvent(@Observes DapSessionEvent event) {
        LOG.infof("DAP session event: %s - session=%s, workspace=%s, status=%s->%s",
            event.getType(), event.getSessionId(), event.getWorkspaceUri(),
            event.getOldStatus(), event.getNewStatus());

        var msg = new DapSessionUpdateWsMessage(
                event.getType().name(),
                event.getSessionId(),
                event.getWorkspaceUri(),
                event.getOldStatus(),
                event.getNewStatus(),
                event.getDebugMode(),
                event.getCreatedBy(),
                event.getCreatedAt(),
                event.getLaunchedBy(),
                event.getLaunchedAt()
            );
        broadcast(msg);
    }

    /**
     * CDI observer for progress updates.
     */
    void onProgressUpdate(@Observes com.redhat.mcp.languagetools.admin.ws.ProgressUpdateWsMessage msg) {
        broadcast(msg);
    }

    /**
     * CDI observer for progress initialization (steps definition).
     */
    void onProgressInit(@Observes com.redhat.mcp.languagetools.admin.ws.ProgressInitWsMessage msg) {
        broadcast(msg);
    }

    /**
     * CDI observer for workspace changes (created/closed).
     */
    void onWorkspaceChange(@Observes WorkspaceChangeEvent event) {
        LOG.infof("Workspace changed: %s - %s", event.type(), event.workspaceUri());

        // Send full workspace list (simpler than delta updates)
        WorkspacesUpdateWsMessage msg = new WorkspacesUpdateWsMessage(
                getCurrentWorkspaces()
        );
        broadcast(msg);

        // Also send updated MCP clients list (tied to workspaces)
        McpClientsUpdateWsMessage clientsMsg = new McpClientsUpdateWsMessage(
                getCurrentMcpClients()
        );
        broadcast(clientsMsg);
    }

    /**
     * CDI observer for server status changes.
     */
    void onServerStatusChange(@Observes LspServerStatusChangeEvent event) {
        LOG.infof("WebSocket: Server status changed: %s/%s - %s -> %s (broadcasting to %d clients)",
                event.workspaceUri(), event.serverId(), event.oldStatus(), event.newStatus(), sessions.size());

        // Get server details for progress info
        var workspace = application.getWorkspace(event.workspaceUri());
        String statusMessage = null;
        Double installProgress = null;
        Boolean isReady = false;

        if (workspace != null) {
            var server = workspace.getLspServer(event.serverId());
            if (server != null) {
                statusMessage = server.getStatusMessage();
                isReady = server.isReady();

                // Get install progress if installing
                if (event.newStatus() == com.redhat.mcp.languagetools.server.ServerStatus.INSTALLING) {
                    var config = server.getConfig();
                    var progressIndicator = config.getInstallProgress();
                    if (progressIndicator != null) {
                        installProgress = progressIndicator.getFraction();
                    }
                }
            }
        }

        // Send status change event with progress info
        ServerStatusChangedWsMessage msg = new ServerStatusChangedWsMessage(
                event.workspaceUri().toString(),
                event.serverId(),
                event.oldStatus().name(),
                event.newStatus().name(),
                statusMessage,
                installProgress,
                isReady
        );
        broadcast(msg);

        // Also send full workspace list to keep UI in sync
        WorkspacesUpdateWsMessage workspacesMsg = new WorkspacesUpdateWsMessage(
                getCurrentWorkspaces()
        );
        broadcast(workspacesMsg);
    }

    /**
     * Broadcast message to all connected sessions.
     */
    private void broadcast(Object message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize message: %s", message);
            return;
        }

        sessions.forEach(session -> {
            if (session.isOpen()) {
                // Use async send with callback for backpressure handling
                session.getAsyncRemote().sendText(json, result -> {
                    if (!result.isOK()) {
                        LOG.warnf("Failed to send to session %s: %s",
                                session.getId(), result.getException().getMessage());

                        // Close slow clients that can't keep up
                        try {
                            session.close(new CloseReason(
                                    CloseReason.CloseCodes.TRY_AGAIN_LATER,
                                    "Client too slow"
                            ));
                        } catch (IOException e) {
                            // Session already closed
                        }
                        sessions.remove(session);
                    }
                });
            } else {
                sessions.remove(session);
            }
        });
    }

    /**
     * Send message to a specific session.
     */
    private void sendToSession(Session session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.getAsyncRemote().sendText(json);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send message to session: %s", session.getId());
        }
    }

    /**
     * Get current workspaces (copied from AdminResource logic).
     */
    private List<WorkspaceDTO> getCurrentWorkspaces() {
        return application.getWorkspaces()
                .stream()
                .map(this::toWorkspaceDTO)
                .toList();
    }

    /**
     * Get current MCP clients (copied from McpClientsResource logic).
     */
    private List<McpClientDTO> getCurrentMcpClients() {
        List<McpClientDTO> clients = new ArrayList<>();

        for (McpConnectionBase connection : connectionManager) {
            var initialRequest = connection.initialRequest();

            String name = "Unknown";
            String version = null;
            String protocolVersion = null;

            if (initialRequest != null) {
                if (initialRequest.implementation() != null) {
                    name = initialRequest.implementation().name();
                    version = initialRequest.implementation().version();
                }
                protocolVersion = initialRequest.protocolVersion().toString();
            }

            clients.add(new McpClientDTO(
                    connection.id(),
                    name,
                    version,
                    protocolVersion,
                    null  // connectedAt not persisted
            ));
        }

        return clients;
    }

    /**
     * Convert workspace to DTO (copied from AdminResource).
     */
    private WorkspaceDTO toWorkspaceDTO(Workspace workspace) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        List<WorkspaceDTO.McpClientInfo> mcpClients = workspace.getMcpClientConnections().values()
                .stream()
                .map(clientInfo -> new WorkspaceDTO.McpClientInfo(
                        clientInfo.name(),
                        formatter.format(clientInfo.connectedAt())
                ))
                .toList();

        var uri = workspace.getNormalizedUri();
        return new WorkspaceDTO(uri, workspace.isInitialized(), mcpClients);
    }
}
