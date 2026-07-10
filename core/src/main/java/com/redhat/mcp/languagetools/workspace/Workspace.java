package com.redhat.mcp.languagetools.workspace;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.dap.session.DapTraceCollectorWrapper;
import com.redhat.mcp.languagetools.installer.*;
import com.redhat.mcp.languagetools.lsp.LspInstanceRegistry;
import com.redhat.mcp.languagetools.lsp.server.*;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.server.ServerBase;
import com.redhat.mcp.languagetools.server.ServerStatus;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents a workspace (project) with its language server and debug adapter instances.
 * A workspace can have multiple LSP servers (e.g., JDT.LS + Qute LS) and DAP servers (e.g., vscode-js-debug).
 */
public class Workspace {

    private static final Logger LOG = Logger.getLogger(Workspace.class);

    private final Application application;

    // Workspace
    private final URI rootUri;
    private final String normalizedRootUriString; // Cached normalized URI string (no trailing slash)
    private final Path workspaceDataDir;
    private final WorkspaceConfiguration configuration;

    // LSP
    private final LspTraceCollector lspTraceCollector;

    private final Map<String, LspServer> lspServers = new ConcurrentHashMap<>();
    private final Map<String, McpClientInfo> mcpClientConnections = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private Consumer<LspServerStatusChangeEvent> statusChangeCallback;

    public record McpClientInfo(
            String connectionId,
            String name,
            Instant connectedAt
    ) {

    }

    public Workspace(URI rootUri,
                     Application application) {
        this.rootUri = rootUri;
        // Cache normalized URI string (remove trailing slash for consistency across the app)
        this.normalizedRootUriString = rootUri.toString();
        this.application = application;
        this.workspaceDataDir = createWorkspaceDataDir(application.getPathManager().getWorkspaceDataDir(), rootUri);
        this.lspTraceCollector = application.getLspTraceCollector();
        this.configuration = new WorkspaceConfiguration(Paths.get(rootUri));
    }

    /**
     * Set callback for LSP server status changes.
     */
    public void setServerStatusChangeCallback(Consumer<LspServerStatusChangeEvent> callback) {
        this.statusChangeCallback = callback;
    }

    /**
     * Register status change callback for a server.
     * Factorized method to avoid code duplication.
     * Works for both LSP and DAP servers since they both extend ServerBase.
     */
    private void registerServerStatusCallback(ServerBase<?> server) {
        server.addStatusChangeListener((oldStatus, newStatus) -> {
            if (statusChangeCallback != null) {
                statusChangeCallback.accept(new LspServerStatusChangeEvent(
                        rootUri,
                        server.getId(),
                        oldStatus,
                        newStatus
                ));
            }
        });
    }

    /**
     * Add an LSP server to this workspace (serverHome calculated from PathManager).
     *
     * @param config Server configuration
     * @return
     */
    public LspServer addLspServer(LspServerConfig config) {
        // TraceCollector is now configured in createLspServer()
        var lspServer = createLspServer(config);
        LOG.infof("Added LSP server '%s' to workspace: %s", config.getServerId(), rootUri);
        return lspServer;
    }


    /**
     * Restart a specific LSP server (shutdown old, create new, start).
     * Will try to connect to IDE instance if available.
     *
     * @param serverId Server ID
     * @param progressMonitor Progress monitor (never null)
     */
    public CompletableFuture<Void> restartLspServer(String serverId, ProgressMonitor progressMonitor) {
        LspServerConfig serverConfig = application.getLspServerConfig(serverId);
        if (serverConfig == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Server not found: " + serverId));
        }

        LspServer oldServer = getLspServer(serverId);

        // Shutdown old server if it exists
        CompletableFuture<Void> shutdownFuture;
        if (oldServer != null && oldServer.getStatus() != ServerStatus.STOPPED) {
            shutdownFuture = oldServer.shutdown();
        } else {
            shutdownFuture = CompletableFuture.completedFuture(null);
        }

        return shutdownFuture.thenCompose(v -> {
            // Create new server instance using factory
            LspServer newServer = createLspServer(serverConfig);
            lspServers.put(serverId, newServer);

            // Start and initialize (will detect IDE instance)
            return newServer.start(progressMonitor)
                    .thenCompose(initV -> newServer.initialize())
                    .thenRun(() -> LOG.infof("Restarted LSP server '%s' for workspace: %s", serverId, rootUri))
                    .exceptionally(ex -> {
                        LOG.errorf(ex, "Failed to restart LSP server '%s'", serverId);
                        throw new RuntimeException("Failed to restart server: " + ex.getMessage(), ex);
                    });
        });
    }

    /**
     * Start an MCP-managed LSP server only (do not connect to IDE instance).
     * Handles installation if needed before starting.
     *
     * @param serverId Server ID
     * @param progressMonitor Progress monitor (never null)
     */
    public CompletableFuture<Void> startManagedLspServer(String serverId, ProgressMonitor progressMonitor) {
        LspServerConfig serverConfig = application.getLspServerConfig(serverId);
        if (serverConfig == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Server not found: " + serverId));
        }

        progressMonitor.reportProgress("Starting " + serverConfig.getName() + "...");

        LspServer oldServer = getLspServer(serverId);

        // Shutdown old server if it exists
        CompletableFuture<Void> shutdownFuture;
        if (oldServer != null && oldServer.getStatus() != ServerStatus.STOPPED) {
            shutdownFuture = oldServer.shutdown();
        } else {
            shutdownFuture = CompletableFuture.completedFuture(null);
        }

        return shutdownFuture
                .thenCompose(v -> {
            // Create new server instance BEFORE installation so we can set INSTALLING status
            LspServer newServer = createLspServer(serverConfig);

            // Set status to INSTALLING if installer exists (BEFORE adding to lspServers map)
            if (serverConfig.getInstaller() != null) {
                newServer.setStatus(ServerStatus.INSTALLING);
            }


            // Step 1: Start and initialize (installation happens inside start())
            return newServer.startManagedOnly(progressMonitor)
                    .thenCompose(initV -> newServer.initialize())
                    .thenRun(() -> LOG.infof("Started MCP-managed LSP server '%s' for workspace: %s", serverId, rootUri))
                    .exceptionally(ex -> {
                        LOG.errorf(ex, "Failed to start MCP-managed LSP server '%s'", serverId);

                        // Update server status based on the type of failure
                        Throwable cause = ex.getCause();
                        if (cause instanceof InstallationException
                                || ex instanceof InstallationException) {
                            newServer.setStatus(ServerStatus.INSTALL_FAILED);
                        } else {
                            newServer.setStatus(ServerStatus.START_FAILED);
                        }

                        throw new RuntimeException("Failed to start managed server: " + ex.getMessage(), ex);
                    });
        });
    }

    /**
     * Ensure an LSP server is started in this workspace.
     * Handles:
     * - Checking for external instances (launched by IDE)
     * - Installing if needed
     * - Starting and initializing the server
     *
     * @param serverId        The server ID to ensure is started
     * @param progressMonitor
     * @return CompletableFuture<LspServer> that completes when server is started (not necessarily ready)
     */
    public CompletableFuture<LspServer> ensureLspServerStarted(String serverId,
                                                               ProgressMonitor progressMonitor) {
        // Already running?
        if (hasLspServer(serverId)) {
            LspServer server = getLspServer(serverId);
            if (server != null && server.getStatus() != ServerStatus.STOPPED) {
                LOG.debugf("Server '%s' already running in workspace: %s", serverId, rootUri);
                return CompletableFuture.completedFuture(server);
            }
        }

        LOG.infof("Ensuring server '%s' is started in workspace: %s", serverId, rootUri);

        LspServerConfig config = application.getLspServerConfig(serverId);
        if (config == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Server config not found: " + serverId)
            );
        }

        // Check if there's an external instance (launched by IDE) first
        var externalInstance = getExternalInstance(serverId);
        if (externalInstance != null) {
            LOG.infof("Found external %s instance (port %d, PID %d), connecting...",
                config.getName(), externalInstance.port, externalInstance.pid);

            // Add server to workspace if not already present
            if (!hasLspServer(serverId)) {
                addLspServer(config);
            }

            // Start and initialize (will connect to socket)
            var server = getLspServer(serverId);
            if (server != null) {
                return server.start(progressMonitor)
                    .thenCompose(v -> server.initialize())
                    .thenApply(v -> server)
                    .exceptionally(ex -> {
                        LOG.errorf(ex, "Failed to connect to external %s", config.getName());
                        return null;
                    });
            }
        }

        // No external instance - start our own managed server (handles installation automatically)
        return startManagedLspServer(serverId, progressMonitor)
            .thenApply(v -> {
                LspServer server = getLspServer(serverId);
                if (server == null) {
                    throw new IllegalStateException("Server failed to start: " + serverId);
                }
                return server;
            });
    }

    /**
     * Ensure an LSP server is started and ready in this workspace.
     * This method calls ensureLspServerStarted() and waits until the server is ready.
     * Handles:
     * - Checking for external instances (launched by IDE)
     * - Installing if needed
     * - Starting, initializing and waiting for the server to be ready
     *
     * @param serverId The server ID to ensure is ready
     * @return CompletableFuture<LspServer> that completes when server is ready
     */
    public CompletableFuture<LspServer> ensureLspServerReady(String serverId,
                                                             ProgressMonitor progressMonitor) {
        return ensureLspServerStarted(serverId, progressMonitor)
            .thenCompose(server -> server.waitUntilReady().thenApply(v -> server));
    }

    /**
     * Shutdown the workspace (stop all LSP servers).
     */
    public CompletableFuture<Void> shutdown() {
        LOG.infof("Shutting down workspace: %s", rootUri);
        initialized = false;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (LspServer server : lspServers.values()) {
            futures.add(server.shutdown());
        }

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    lspServers.clear();
                    LOG.infof("Workspace shut down: %s", rootUri);
                });
    }

    /**
     * Check if workspace has a language server for the given ID.
     */
    public boolean hasLspServer(String serverId) {
        return lspServers.containsKey(serverId);
    }

    /**
     * Get a language server by ID.
     */
    public LspServer getLspServer(String id) {
        return lspServers.get(id);
    }

    /**
     * Add a DAP server configuration to this workspace.
     * DAP servers are not started automatically - they are started on-demand during debug sessions.
     */
    public void addDapServer(DapServerConfig config) {
        // Set trace collector for installation support
        if (config.getTraceCollector() == null) {
            // Create a TraceCollector wrapper around DapTraceCollector
            config.setTraceCollector(new DapTraceCollectorWrapper(
                application.getDapTraceCollector(),
                rootUri.toString(),
                config.getServerId()
            ));
        }
        LOG.infof("Added DAP server to workspace %s: %s", rootUri, config.getServerId());
    }

    /**
     * Get status for a server.
     */
    public ServerStatus getLspServerStatus(String serverId) {
        LspServer server = getLspServer(serverId);
        return server != null ? server.getStatus() : ServerStatus.STOPPED;
    }

    /**
     * Get external instance info for a server (launched by an IDE).
     */
    public LspInstanceRegistry.InstanceInfo getExternalInstance(String serverId) {
        try {
            String workspacePath = Paths.get(rootUri).toString();
            return LspInstanceRegistry.findInstance(workspacePath, serverId);
        } catch (Exception e) {
            LOG.debugf("Failed to check for external instance of %s: %s", serverId, e.getMessage());
            return null;
        }
    }

    public URI getRootUri() {
        return rootUri;
    }

    /**
     * Get normalized root URI string (without trailing slash).
     * Use this method for consistency when sending URIs to the frontend or comparing URIs.
     */
    public String getNormalizedUri() {
        return normalizedRootUriString;
    }

    public Path getWorkspaceDataDir() {
        return workspaceDataDir;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get workspace configuration (reads from .vscode/settings.json).
     */
    public WorkspaceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Add an MCP client to this workspace.
     *
     * @param connectionId MCP connection ID
     * @param clientName   Client name (e.g., "claude-code 2.1.183")
     * @return true if this is a new client, false if it already existed
     */
    public boolean addMcpClient(String connectionId, String clientName) {
        if (connectionId != null && !connectionId.isEmpty()) {
            boolean isNew = !mcpClientConnections.containsKey(connectionId);
            if (isNew) {
                mcpClientConnections.put(connectionId, new McpClientInfo(
                        connectionId,
                        clientName,
                        java.time.Instant.now()
                ));
                LOG.infof("Added MCP client '%s' [%s] to workspace: %s (total: %d)",
                        clientName, connectionId, rootUri, mcpClientConnections.size());
            } else {
                LOG.debugf("MCP client '%s' [%s] already connected to workspace: %s",
                        clientName, connectionId, rootUri);
            }
            return isNew;
        }
        return false;
    }

    /**
     * Get all MCP client connections.
     */
    public Map<String, McpClientInfo> getMcpClientConnections() {
        return Collections.unmodifiableMap(mcpClientConnections);
    }

    private Path createWorkspaceDataDir(Path baseDir, URI rootUri) {
        try {
            String workspaceName = Path.of(rootUri).getFileName().toString();
            Path dir = baseDir.resolve(workspaceName + "-" + Math.abs(rootUri.hashCode()));
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create workspace data directory", e);
        }
    }

    public Application getApplication() {
        return application;
    }

    // LSP servers

    public Collection<LspServer> getLspServers() {
        return lspServers.values();
    }

    private LspServer createLspServer(LspServerConfig serverConfig) {
        // Set trace collector for installation and LSP communication support
        if (serverConfig.getTraceCollector() == null) {
            // Create a TraceCollector wrapper around LspTraceCollector
            serverConfig.setTraceCollector(new LspTraceCollectorWrapper(lspTraceCollector, rootUri.toString(), serverConfig.getServerId()));
        }

        // Create new server instance using factory
        LspServer newServer = LspServerFactoryRegistry.getInstance().createServer(serverConfig, this);
        lspServers.put(newServer.getId(), newServer);
        // Register status change callback
        registerServerStatusCallback(newServer);
        return newServer;
    }

    // DAP servers

}

