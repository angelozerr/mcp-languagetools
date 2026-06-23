package com.redhat.mcp.languagetools.admin;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.event.Observes;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.redhat.mcp.languagetools.admin.dto.LspServerDTO;
import com.redhat.mcp.languagetools.admin.dto.WorkspaceDTO;
import com.redhat.mcp.languagetools.workspace.Workspace;
import com.redhat.mcp.languagetools.workspace.WorkspaceManager;
import com.redhat.mcp.languagetools.workspace.WorkspaceChangeEvent;
import com.redhat.mcp.languagetools.lsp.server.LspServerStatusChangeEvent;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    @Inject
    WorkspaceManager workspaceManager;

    private final List<io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor<List<WorkspaceDTO>>> subscribers = new CopyOnWriteArrayList<>();

    @GET
    @Path("/workspaces")
    public List<WorkspaceDTO> listWorkspaces() {
        return getCurrentWorkspaces();
    }

    /**
     * SSE stream for real-time workspace changes.
     */
    @GET
    @Path("/workspaces/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<List<WorkspaceDTO>> streamWorkspaces() {
        var processor = io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor.<List<WorkspaceDTO>>create();
        subscribers.add(processor);

        // Send current state immediately
        processor.onNext(getCurrentWorkspaces());

        return processor
                .onCancellation().invoke(() -> subscribers.remove(processor))
                .onTermination().invoke(() -> subscribers.remove(processor));
    }

    /**
     * CDI observer to broadcast workspace changes to SSE subscribers.
     */
    void onWorkspaceChange(@Observes WorkspaceChangeEvent event) {
        LOG.infof("Workspace change detected: %s %s", event.type(), event.workspaceUri());
        broadcastWorkspaces();
    }

    /**
     * CDI observer to broadcast workspace updates when MCP clients change.
     * (Workspaces contain list of connected MCP clients)
     */
    void onMcpClientChange(@Observes McpClientChangeEvent event) {
        LOG.debug("MCP client change detected, updating workspaces");
        broadcastWorkspaces();
    }

    /**
     * CDI observer to broadcast workspace updates when LSP server status changes.
     * (Workspaces contain list of servers with their status)
     */
    void onLspServerStatusChange(@Observes LspServerStatusChangeEvent event) {
        LOG.debugf("LSP server status change: %s %s -> %s",
                  event.serverId(), event.oldStatus(), event.newStatus());
        broadcastWorkspaces();
    }

    private void broadcastWorkspaces() {
        List<WorkspaceDTO> currentWorkspaces = getCurrentWorkspaces();
        subscribers.forEach(processor -> processor.onNext(currentWorkspaces));
    }

    private List<WorkspaceDTO> getCurrentWorkspaces() {
        return workspaceManager.getWorkspaces().entrySet().stream()
                .map(entry -> toDTO(entry.getKey(), entry.getValue()))
                .toList();
    }

    @GET
    @Path("/workspaces/{uri}")
    public WorkspaceDTO getWorkspace(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);
        Workspace workspace = workspaceManager.getWorkspaces().get(uri);
        if (workspace == null) {
            throw new NotFoundException("Workspace not found: " + uri);
        }
        return toDTO(uri, workspace);
    }

    /**
     * Close a workspace: shutdown all its LSP servers and remove from memory.
     */
    @DELETE
    @Path("/workspaces/{uri}")
    public Response closeWorkspace(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);

        workspaceManager.closeWorkspace(uri).join();

        return Response.ok()
                .entity("{\"status\": \"closed\", \"uri\": \"" + uri + "\"}")
                .build();
    }

    private WorkspaceDTO toDTO(URI uri, Workspace workspace) {
        // Get all available server descriptors
        var allServerConfigs = workspaceManager.getServerConfigs();

        // Map to DTOs with actual status (INSTALLING, STARTING, RUNNING, or STOPPED)
        List<LspServerDTO> servers = allServerConfigs.values().stream()
                .map(config -> {
                    LspServerDTO.ExternalInstanceInfo externalInfo = null;
                    Long pid = null;
                    String command = null;

                    // Only show external instance info if server is already connected
                    var lspServer = workspace.getLspServer(config.getId());
                    if (lspServer != null) {
                        var currentInstance = lspServer.getCurrentInstance();
                        if (currentInstance != null) {
                            externalInfo = new LspServerDTO.ExternalInstanceInfo(
                                currentInstance.port,
                                currentInstance.pid,
                                true,
                                currentInstance.clientName,
                                currentInstance.clientVersion
                            );
                        }

                        // Get PID and command from running server
                        pid = lspServer.getPid();
                        command = lspServer.getStartCommand();
                    }

                    return new LspServerDTO(
                        config.getId(),
                        config.getName(),
                        workspace.getServerStatus(config.getId()),
                        externalInfo,
                        pid,
                        command
                    );
                })
                .toList();

        // Build MCP client info with timestamps
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ISO_INSTANT;
        java.util.List<WorkspaceDTO.McpClientInfo> mcpClients = workspace.getMcpClientConnections().values().stream()
                .map(clientInfo -> new WorkspaceDTO.McpClientInfo(
                    clientInfo.name(),
                    formatter.format(clientInfo.connectedAt())
                ))
                .toList();

        LOG.infof("Workspace %s - mcpClients: %s", uri, mcpClients);
        return new WorkspaceDTO(uri, workspace.isInitialized(), mcpClients, servers);
    }
}
