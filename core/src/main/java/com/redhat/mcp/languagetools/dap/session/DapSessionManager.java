package com.redhat.mcp.languagetools.dap.session;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.workspace.Workspace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple DAP debug sessions across workspaces.
 * <p>
 * Responsibilities:
 * - Create/destroy debug sessions
 * - Track active sessions (sessionId -> DapSession)
 * - Route tool calls to the correct session
 * - Match debug adapters to languages/file types
 */
@ApplicationScoped
public class DapSessionManager {

    private static final Logger LOG = Logger.getLogger(DapSessionManager.class);

    @Inject
    Application application;

    @Inject
    DapTraceCollector traceCollector;

    @Inject
    LanguageRegistry languageRegistry;

    @Inject
    Event<DapSessionEvent> sessionEvent;

    private final Map<String, DapSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new debug session for a specific DAP server.
     *
     * @param workspaceUri Workspace URI for context
     * @param dapServerId  DAP server ID (e.g., "debugpy", "vscode-js-debug")
     * @param sessionName  User-friendly name for the session
     * @param createdBy    Who created the session
     * @return Created DapSession
     */
    public DapSession createSession(URI workspaceUri,
                                    String dapServerId,
                                    String sessionName,
                                    DapSession.SessionActor createdBy) {
        LOG.infof("Creating debug session: workspace=%s, dapServerId=%s, name=%s, createdBy=%s",
                workspaceUri, dapServerId, sessionName, createdBy);

        // Find workspace
        Workspace workspace = application.getOrCreateWorkspace(workspaceUri);
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceUri);
        }

        // Find DAP server config
        DapServerConfig serverConfig = findDapServerById(workspace, dapServerId);
        if (serverConfig == null) {
            throw new IllegalArgumentException("DAP server not found: " + dapServerId);
        }

        // Set trace collector for installation support
        String sessionId = UUID.randomUUID().toString();
        if (serverConfig.getTraceCollector() == null) {
            serverConfig.setTraceCollector(traceCollector);
        }

        // Determine language from server config (first supported language)
        String language = extractLanguageFromConfig(serverConfig);

        // Create session
        DapSession session = new DapSession(
                sessionId,
                language,
                sessionName,
                createdBy,
                serverConfig,
                workspace
        );

        sessions.put(sessionId, session);
        LOG.infof("Created session %s for %s (%s)", sessionId, language, sessionName);

        // Register status change listener on the DAP server
        session.getDapServer().addStatusChangeListener((oldStatus, newStatus) -> {
            LOG.infof("DAP server status changed: session=%s, %s -> %s", sessionId, oldStatus, newStatus);
            fireStateChangedEvent(session, oldStatus, newStatus);
        });

        // Register session state change listener
        session.setStateChangeCallback(() -> {
            LOG.infof("DAP session state changed: session=%s, new state=%s", sessionId, session.getState());
            fireStateChangedEvent(session, null, null);
        });

        // Fire CDI event for WebSocket notification
        fireCreatedEvent(session);
        // Don't initialize yet - initialization (including installation) happens on first launch
        return session;
    }

    /**
     * List all active debug sessions.
     */
    public List<Map<String, Object>> listSessions() {
        LOG.debugf("Listing %d active debug sessions", sessions.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (DapSession session : sessions.values()) {
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("sessionId", session.getSessionId());
            sessionInfo.put("serverId", session.getServerConfig().getServerId()); // DAP server ID
            sessionInfo.put("workspaceUri", session.getWorkspace().getNormalizedUri()); // Workspace URI
            sessionInfo.put("language", session.getLanguage());
            sessionInfo.put("sessionName", session.getSessionName());
            sessionInfo.put("state", session.getState().name());
            sessionInfo.put("createdBy", session.getCreatedBy());
            sessionInfo.put("launchedBy", session.getLaunchedBy());
            sessionInfo.put("debugMode", session.isDebugMode());

            // Include timestamps
            if (session.getCreatedAt() != null) {
                sessionInfo.put("createdAt", session.getCreatedAt().toString());
            }
            if (session.getLaunchedAt() != null) {
                sessionInfo.put("launchedAt", session.getLaunchedAt().toString());
            }

            // Include launch configuration if available
            if (session.getLaunchConfiguration() != null) {
                sessionInfo.put("launchConfiguration", session.getLaunchConfiguration());
            }

            result.add(sessionInfo);
        }
        return result;
    }

    /**
     * Get all active sessions.
     */
    public List<DapSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * Get a session by ID (returns null if not found).
     */
    public DapSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Get all sessions for a specific workspace.
     */
    public List<DapSession> getSessionsForWorkspace(URI workspaceUri) {
        return sessions.values().stream()
                .filter(session -> session.getWorkspace().getRootUri().equals(workspaceUri))
                .toList();
    }

    /**
     * Close and remove a debug session.
     */
    public CompletableFuture<Map<String, Object>> closeSession(String sessionId) {
        LOG.infof("Closing debug session: %s", sessionId);

        DapSession session = sessions.remove(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(Map.of(
                    "success", false,
                    "message", "Session not found: " + sessionId
            ));
        }

        return session.terminate()
                .thenApply(v -> {
                    LOG.infof("Session closed: %s", sessionId);
                    fireDeletedEvent(session);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("sessionId", sessionId);
                    result.put("message", "Debug session closed");
                    return result;
                })
                .exceptionally(ex -> {
                    LOG.errorf(ex, "Error closing session %s", sessionId);
                    fireDeletedEvent(session);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("sessionId", sessionId);
                    result.put("message", "Error closing session: " + ex.getMessage());
                    return result;
                });
    }

    /**
     * List all available debug adapters with their details.
     */
    public List<Map<String, Object>> listDebugAdapters() {
        List<Map<String, Object>> adapters = new ArrayList<>();

        for (DapServerConfig config : application.getDapServerConfigs()) {
            adapters.add(buildAdapterInfo(config));
        }

        LOG.debugf("Available debug adapters: %d", adapters.size());
        return adapters;
    }

    /**
     * List debug adapters suitable for a specific file.
     * Uses LanguageRegistry to detect the language automatically.
     */
    public List<Map<String, Object>> listDebugAdaptersForFile(URI fileUri) {
        List<Map<String, Object>> adapters = new ArrayList<>();

        // Create language document (detects language using LanguageRegistry)
        LanguageDocument document = languageRegistry.createDocument(fileUri);
        String language = document.getLanguageId();

        if (language == null) {
            LOG.warnf("Could not detect language for file: %s", fileUri);
            // Return all adapters if language detection fails
            return listDebugAdapters();
        }

        for (DapServerConfig config : application.getDapServerConfigs()) {
            // Check if this adapter supports the file's language
            if (config.canHandle(null, language)) {
                adapters.add(buildAdapterInfo(config));
            }
        }

        LOG.debugf("Debug adapters for file %s (language: %s): %d", fileUri, language, adapters.size());
        return adapters;
    }

    private Map<String, Object> buildAdapterInfo(DapServerConfig config) {
        Map<String, Object> adapter = new HashMap<>();
        adapter.put("id", config.getServerId());
        adapter.put("name", config.getName());
        adapter.put("enabled", true);  // All configs from getDapServerConfigs are enabled

        // Extract supported languages
        List<String> languages = new ArrayList<>();
        if (config.getDocumentSelector() != null) {
            config.getDocumentSelector().forEach(selector -> {
                if (selector.getLanguage() != null && !languages.contains(selector.getLanguage())) {
                    languages.add(selector.getLanguage());
                }
            });
        }
        adapter.put("languages", languages);

        return adapter;
    }


    /**
     * Get statistics about active sessions.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Long> stateCount = new HashMap<>();
        for (DapSession session : sessions.values()) {
            String state = session.getState().name();
            stateCount.put(state, stateCount.getOrDefault(state, 0L) + 1);
        }

        return Map.of(
                "totalSessions", sessions.size(),
                "sessionsByState", stateCount,
                "availableAdapters", application.getDapServerConfigs().size()
        );
    }

    // ========== Helper Methods ==========

    /**
     * Find DAP server by ID.
     */
    private DapServerConfig findDapServerById(Workspace workspace, String dapServerId) {
        // First check global DAP servers
        DapServerConfig config = application.getDapServerConfig(dapServerId);
        if (config != null) {
            return config;
        }

        // Then check workspace-specific DAP servers
        // Map<String, DapServerConfig> workspaceDapServers = workspace.getDapServerConfigs();
        //return workspaceDapServers.get(dapServerId);
        return null;
    }

    /**
     * Extract language from DAP server config (first supported language).
     */
    private String extractLanguageFromConfig(DapServerConfig config) {
        if (config.getDocumentSelector() != null && !config.getDocumentSelector().isEmpty()) {
            String lang = config.getDocumentSelector().get(0).getLanguage();
            if (lang != null) {
                return lang;
            }
        }
        // Fallback: derive from server ID
        return config.getServerId();
    }

    /**
     * Find the appropriate DAP server for a language.
     */
    /**
     * @deprecated Use findDapServerById instead
     */
    @Deprecated
    private DapServerConfig findDapServerForLanguage(Workspace workspace, String language) {
        // First check workspace-specific DAP servers
        var workspaceDapServers = workspace.getApplication().getDapServerConfigs();
        for (DapServerConfig config : workspaceDapServers) {
            if (config.canHandle(null, language)) {
                return config;
            }
        }

        // Fallback to global DAP servers
        /*Map<String, DapServerConfig> globalDapServers = application.getDapServerConfigs();
        for (DapServerConfig config : globalDapServers.values()) {
            if (config.canHandle(null, language)) {
                return config;
            }
        }*/

        return null;
    }

    // ========== Helper Methods ==========

    /**
     * Fire STATE_CHANGED event (with null check).
     */
    private void fireStateChangedEvent(DapSession session, com.redhat.mcp.languagetools.server.ServerStatus oldStatus, com.redhat.mcp.languagetools.server.ServerStatus newStatus) {
        if (sessionEvent != null) {
            sessionEvent.fire(DapSessionEvent.stateChanged(session, oldStatus, newStatus));
            LOG.infof("Fired STATE_CHANGED event for session %s: %s -> %s (debugMode=%s)",
                session.getSessionId(), oldStatus, newStatus, session.isDebugMode());
        } else {
            LOG.errorf("sessionEvent is null! Cannot fire STATE_CHANGED event");
        }
    }

    /**
     * Fire CREATED event (with null check).
     */
    private void fireCreatedEvent(DapSession session) {
        if (sessionEvent != null) {
            sessionEvent.fire(DapSessionEvent.created(session));
            LOG.infof("Fired CREATED event for session %s", session.getSessionId());
        } else {
            LOG.errorf("sessionEvent is null! Cannot fire CREATED event");
        }
    }

    /**
     * Fire DELETED event (with null check).
     */
    private void fireDeletedEvent(DapSession session) {
        if (sessionEvent != null) {
            sessionEvent.fire(DapSessionEvent.deleted(session));
            LOG.infof("Fired DELETED event for session %s", session.getSessionId());
        } else {
            LOG.errorf("sessionEvent is null! Cannot fire DELETED event");
        }
    }

    // ========== Cleanup ==========

    /**
     * Shutdown all active sessions (called on application shutdown).
     */
    public CompletableFuture<Void> shutdownAll() {
        LOG.infof("Shutting down all %d debug sessions", sessions.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (DapSession session : sessions.values()) {
            futures.add(session.terminate());
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    sessions.clear();
                    LOG.info("All debug sessions shut down");
                });
    }
}
