package com.redhat.mcp.languagetools.workspace;

import org.jboss.logging.Logger;

import com.redhat.mcp.languagetools.lsp.LspServer;
import com.redhat.mcp.languagetools.lsp.LspServerConfig;
import com.redhat.mcp.languagetools.lsp.ServerStatus;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a workspace (project) with its language server instances.
 * A workspace can have multiple LSP servers (e.g., JDT.LS + Qute LS).
 */
public class Workspace {

    private static final Logger LOG = Logger.getLogger(Workspace.class);

    private final URI rootUri;
    private final Path workspaceDataDir;
    private final LspTraceCollector traceCollector;
    private final Map<String, LspServer> lspServers = new ConcurrentHashMap<>();
    private final Map<String, ServerInfo> serverInfos = new ConcurrentHashMap<>();
    private final Map<String, ServerStatus> installationStatus = new ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> mcpClientConnections = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    private static class ServerInfo {
        final LspServerConfig config;
        final Path serverHome;

        ServerInfo(LspServerConfig config, Path serverHome) {
            this.config = config;
            this.serverHome = serverHome;
        }
    }

    public Workspace(URI rootUri, Path workspaceDataDir, LspTraceCollector traceCollector) {
        this.rootUri = rootUri;
        this.workspaceDataDir = createWorkspaceDataDir(workspaceDataDir, rootUri);
        this.traceCollector = traceCollector;
    }

    /**
     * Add a language server to this workspace.
     */
    public void addLspServer(LspServerConfig config, Path serverHome) {
        LspServer server = new LspServer(config, rootUri, workspaceDataDir, serverHome, traceCollector);
        lspServers.put(config.getId(), server);
        serverInfos.put(config.getId(), new ServerInfo(config, serverHome));
        LOG.infof("Added LSP server '%s' to workspace: %s", config.getId(), rootUri);
    }

    /**
     * Restart a specific LSP server (shutdown old, create new, start).
     * Will try to connect to IDE instance if available.
     */
    public CompletableFuture<Void> restartLspServer(String serverId) {
        ServerInfo info = serverInfos.get(serverId);
        if (info == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Server not found: " + serverId));
        }

        LspServer oldServer = lspServers.get(serverId);

        return CompletableFuture.runAsync(() -> {
            try {
                // Shutdown old server if it exists and is not already stopped
                if (oldServer != null && oldServer.getStatus() != ServerStatus.STOPPED) {
                    oldServer.shutdown().join();
                }

                // Create new server instance
                LspServer newServer = new LspServer(info.config, rootUri, workspaceDataDir, info.serverHome, traceCollector);
                lspServers.put(serverId, newServer);

                // Start and initialize (will detect IDE instance)
                newServer.start()
                        .thenCompose(v -> newServer.initialize())
                        .join();

                LOG.infof("Restarted LSP server '%s' for workspace: %s", serverId, rootUri);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to restart LSP server '%s'", serverId);
                throw new RuntimeException("Failed to restart server: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Start a MCP-managed LSP server only (do not connect to IDE instance).
     */
    public CompletableFuture<Void> startManagedLspServer(String serverId) {
        ServerInfo info = serverInfos.get(serverId);
        if (info == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Server not found: " + serverId));
        }

        LspServer oldServer = lspServers.get(serverId);

        return CompletableFuture.runAsync(() -> {
            try {
                // Shutdown old server if it exists and is not already stopped
                if (oldServer != null && oldServer.getStatus() != ServerStatus.STOPPED) {
                    oldServer.shutdown().join();
                }

                // Create new server instance
                LspServer newServer = new LspServer(info.config, rootUri, workspaceDataDir, info.serverHome, traceCollector);
                lspServers.put(serverId, newServer);

                // Start MCP-managed only (skip IDE instance detection)
                newServer.startManagedOnly()
                        .thenCompose(v -> newServer.initialize())
                        .join();

                LOG.infof("Started MCP-managed LSP server '%s' for workspace: %s", serverId, rootUri);

            } catch (Exception e) {
                LOG.errorf(e, "Failed to start MCP-managed LSP server '%s'", serverId);
                throw new RuntimeException("Failed to start managed server: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Initialize the workspace (start all LSP servers).
     */
    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("Initializing workspace: %s", rootUri);

        // Start all servers in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (LspServer server : lspServers.values()) {
            CompletableFuture<Void> future = server.start()
                    .thenCompose(v -> server.initialize());
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    initialized = true;
                    LOG.infof("Workspace initialized: %s", rootUri);
                })
                .exceptionally(ex -> {
                    LOG.errorf(ex, "Failed to initialize workspace: %s", rootUri);
                    throw new RuntimeException("Failed to initialize workspace: " + rootUri, ex);
                });
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

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    lspServers.clear();
                    LOG.infof("Workspace shut down: %s", rootUri);
                });
    }

    /**
     * Get a language server by ID.
     */
    public LspServer getLspServer(String id) {
        return lspServers.get(id);
    }

    /**
     * Set installation status for a server.
     */
    public void setInstallationStatus(String serverId, ServerStatus status) {
        if (status == null) {
            installationStatus.remove(serverId);
        } else {
            installationStatus.put(serverId, status);
        }
    }

    /**
     * Get status for a server (running server or installation status).
     */
    public ServerStatus getServerStatus(String serverId) {
        // Check if server is running
        LspServer server = lspServers.get(serverId);
        if (server != null) {
            return server.getStatus();
        }

        // Check installation status
        ServerStatus installStatus = installationStatus.get(serverId);
        if (installStatus != null) {
            return installStatus;
        }

        // Default: stopped
        return ServerStatus.STOPPED;
    }

    /**
     * Get all language servers.
     */
    public Map<String, LspServer> getAllLspServers() {
        return Map.copyOf(lspServers);
    }

    /**
     * Get external instance info for a server (launched by an IDE).
     */
    public com.redhat.mcp.languagetools.lsp.LspInstanceRegistry.InstanceInfo getExternalInstance(String serverId) {
        try {
            String workspacePath = java.nio.file.Paths.get(rootUri).toString();
            return com.redhat.mcp.languagetools.lsp.LspInstanceRegistry.findInstance(workspacePath, serverId);
        } catch (Exception e) {
            LOG.debugf("Failed to check for external instance of %s: %s", serverId, e.getMessage());
            return null;
        }
    }

    public URI getRootUri() {
        return rootUri;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Add an MCP client name to this workspace.
     */
    public void addMcpClient(String clientName) {
        if (clientName != null && !clientName.isEmpty()) {
            mcpClientConnections.put(clientName, java.time.Instant.now());
            LOG.infof("Added MCP client '%s' to workspace: %s (total: %d)",
                     clientName, rootUri, mcpClientConnections.size());
        }
    }

    /**
     * Get all MCP client connections with their timestamps.
     */
    public Map<String, java.time.Instant> getMcpClientConnections() {
        return java.util.Collections.unmodifiableMap(mcpClientConnections);
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
}

