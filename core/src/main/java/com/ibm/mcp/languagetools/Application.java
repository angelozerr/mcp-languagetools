/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.languagetools;

import com.ibm.mcp.languagetools.dap.server.DapServerConfig;
import com.ibm.mcp.languagetools.installer.InstallResult;
import com.ibm.mcp.languagetools.installer.TraceProgressMonitor;
import com.ibm.mcp.languagetools.language.LanguageRegistry;
import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerConfig;
import com.ibm.mcp.languagetools.lsp.server.LspServerStatusChangeEvent;
import com.ibm.mcp.languagetools.mcp.McpClientChangeEvent;
import com.ibm.mcp.languagetools.mcp.McpClientTracker;
import com.ibm.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.ibm.mcp.languagetools.progress.ProgressBroadcaster;
import com.ibm.mcp.languagetools.progress.ProgressMonitor;
import com.ibm.mcp.languagetools.progress.ProgressStep;
import com.ibm.mcp.languagetools.server.ServerConfigBase;
import com.ibm.mcp.languagetools.server.ServerConfigInstalledEvent;
import com.ibm.mcp.languagetools.server.ServerConfigListener;
import com.ibm.mcp.languagetools.server.ServerDescriptorRegistry;
import com.ibm.mcp.languagetools.configuration.ApplicationConfiguration;
import com.ibm.mcp.languagetools.configuration.Configuration;
import com.ibm.mcp.languagetools.configuration.ServerTrace;
import com.ibm.mcp.languagetools.trace.NoOpTraceCollectorFactory;
import com.ibm.mcp.languagetools.trace.TraceCollector;
import com.ibm.mcp.languagetools.trace.TraceCollectorFactory;
import com.ibm.mcp.languagetools.workspace.Workspace;
import com.ibm.mcp.languagetools.workspace.WorkspaceChangeEvent;
import com.ibm.mcp.languagetools.workspace.WorkspaceConfigurationProvider;
import com.ibm.mcp.languagetools.workspace.WorkspaceConfigurationProviderRegistry;
import com.ibm.mcp.languagetools.workspace.WorkspaceConfigurationStrategy;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple workspaces, each with its own language server instances.
 * Workspaces are created dynamically on-demand.
 */
@ApplicationScoped
public class Application {

    private static final Logger LOG = Logger.getLogger(Application.class);

    // Global
    @Inject
    LanguageRegistry languageRegistry;

    @Inject
    PathManager pathManager;

    @Inject
    ApplicationConfiguration applicationConfiguration;

    @Inject
    ServerDescriptorRegistry serverDescriptorRegistry;

    // Workspace
    @Inject
    Event<WorkspaceChangeEvent> workspaceChangeEvent;

    // ----------- LSP servers

    @Inject
    Event<LspServerStatusChangeEvent> lspServerStatusChangeEvent;

    // ----------- DAP servers

    // ----------- MCP servers

    @Inject
    McpClientTracker mcpClientTracker;

    @Inject
    Event<McpClientChangeEvent> mcpClientChangeEvent;

    @Inject
    jakarta.enterprise.inject.Instance<ProgressBroadcaster> progressBroadcasterInstance;

    private final Map<URI, Workspace> workspaces = new ConcurrentHashMap<>();
    private final Map<String, LspServerConfig> lspServerConfigs = new ConcurrentHashMap<>();
    private final Map<String, DapServerConfig> dapServerConfigs = new ConcurrentHashMap<>();

    private final ContributionManager contributionManager;
    private final List<ServerConfigListener> serverConfigListeners = new ArrayList<>();

    // Trace collectors loaded via SPI
    private final TraceCollector lspTraceCollector;
    private final TraceCollector dapTraceCollector;
    private final McpTraceCollector mcpTraceCollector;

    public Application() {
        this.contributionManager = new ContributionManager(this);
        TraceCollectorFactory factory = ServiceLoader.load(TraceCollectorFactory.class)
                .findFirst()
                .orElse(new NoOpTraceCollectorFactory());
        this.lspTraceCollector = factory.createLspTraceCollector();
        this.dapTraceCollector = factory.createDapTraceCollector();
        this.mcpTraceCollector = factory.createMcpTraceCollector();
        LOG.infof("TraceCollectorFactory: %s (enabled=%s)", factory.getClass().getSimpleName(), lspTraceCollector.isEnabled());
    }

    public void addServerConfigListener(ServerConfigListener listener) {
        serverConfigListeners.add(listener);
    }

    public void removeServerConfigListener(ServerConfigListener listener) {
        serverConfigListeners.remove(listener);
    }

    public void fireOnInstalled(ServerConfigBase config, InstallResult result) {
        var event = new ServerConfigInstalledEvent(config, result);
        for (ServerConfigListener listener : serverConfigListeners) {
            try {
                listener.onInstalled(event);
            } catch (Exception e) {
                LOG.warnf(e, "ServerConfigListener.onInstalled failed for '%s'", config.getServerId());
            }
        }
    }

    void onStart(@Observes StartupEvent ignoredEv) {
        LOG.info("ApplicationManager starting...");

        // Load all bundled LSP server descriptors
        lspServerConfigs.putAll(serverDescriptorRegistry.loadAllLspServers(this));

        // Load all bundled DAP server descriptors
        dapServerConfigs.putAll(serverDescriptorRegistry.loadAllDapServers(this));

        LOG.infof("Loaded %d LSP server descriptors", lspServerConfigs.size());
        LOG.infof("Loaded %d DAP server descriptors", dapServerConfigs.size());
    }

    void onShutdown(@Observes ShutdownEvent ev) {
        LOG.info("Shutting down all workspaces...");
        shutdownAll().join();
    }

    /**
     * Get or create a workspace for the given root URI.
     * Workspace is created but NOT initialized (servers added on-demand).
     */
    public Workspace getOrCreateWorkspace(URI rootUri) {
        // Normalize URI
        URI workspaceUri = normalizeUri(rootUri);

        // Get or create workspace (without initialization)
        boolean isNewWorkspace = !workspaces.containsKey(workspaceUri);

        Workspace workspace = workspaces.computeIfAbsent(workspaceUri, uri -> {
            Workspace ws = new Workspace(uri, this);

            // Register callback for LSP server status changes
            ws.setServerStatusChangeCallback(event -> {
                LOG.infof("WorkspaceManager: Firing LSP server status change event: %s/%s - %s -> %s",
                        event.workspaceUri(), event.serverId(), event.oldStatus(), event.newStatus());
                lspServerStatusChangeEvent.fire(event);
            });

            LOG.infof("Created workspace %s", uri);
            return ws;
        });

        // Add current MCP client to this workspace
        String clientName = mcpClientTracker.getCurrentClientName();
        String connectionId = mcpClientTracker.getCurrentConnectionId();

        LOG.infof("Adding MCP client to workspace %s: name=%s, connectionId=%s",
                workspaceUri, clientName, connectionId);

        boolean isNewClient = workspace.addMcpClient(connectionId, clientName);

        // Fire event if a new client was added
        if (isNewClient) {
            LOG.infof("New MCP client added to workspace, firing McpClientChangeEvent");
            mcpClientChangeEvent.fire(new McpClientChangeEvent());
        } else {
            LOG.infof("MCP client already exists in workspace, no event fired");
        }

        // Fire event if workspace was just created
        if (isNewWorkspace) {
            sendWorkspaceChangeEvent(WorkspaceChangeEvent.Type.CREATED, workspaceUri);
        }

        return workspace;
    }

    /**
     * Ensure LSP server is running for the given file in the workspace.
     * Detects language, finds matching servers, installs if needed, and starts them.
     */
    private CompletableFuture<Void> ensureServerForFile(URI fileUri,
                                                        Workspace workspace,
                                                        ProgressMonitor progressMonitor) {
        // Detect language from file
        Optional<String> languageId = languageRegistry.detectLanguage(fileUri);
        if (languageId.isEmpty()) {
            LOG.debugf("No language detected for: %s", fileUri);
            return CompletableFuture.completedFuture(null);
        }

        String language = languageId.get();
        LOG.debugf("Detected language '%s' for: %s", language, fileUri);

        // Collect configs that need starting
        Path basePath = workspace.getRootPath();
        List<LspServerConfig> configsToStart = new ArrayList<>();
        for (LspServerConfig config : lspServerConfigs.values()) {
            if (config.isContributionOnly()) {
                continue;
            }
            if (config.canHandle(fileUri, language, basePath)) {
                if (!workspace.hasLspServer(config.getServerId())) {
                    configsToStart.add(config);
                }
            }
        }

        // Also find and add matching DAP servers (without starting them)
        ensureDapServersForFile(workspace, fileUri, language);

        if (configsToStart.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // Split progress range across servers so each has its own sub-range
        int count = configsToStart.size();
        List<CompletableFuture<Void>> serverFutures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LspServerConfig config = configsToStart.get(i);
            LOG.infof("Need %s for language '%s' in workspace: %s",
                    config.getName(), language, workspace.getNormalizedUri());

            double start = (double) i / count;
            double end = (double) (i + 1) / count;
            ProgressMonitor serverMonitor = count > 1
                    ? progressMonitor.createSubMonitor(start, end)
                    : progressMonitor;

            CompletableFuture<Void> future = workspace.ensureLspServerStarted(
                            config.getServerId(),
                            serverMonitor)
                    .thenAccept(server -> {
                    })
                    .exceptionally(ex -> {
                        LOG.errorf(ex, "Failed to start %s", config.getName());
                        return null;
                    });
            serverFutures.add(future);
        }

        return CompletableFuture.allOf(serverFutures.toArray(new CompletableFuture[0]));
    }

    /**
     * Find and add matching DAP servers to the workspace for the given file.
     * DAP servers are NOT started - they are added to the workspace configuration only.
     */
    private void ensureDapServersForFile(Workspace workspace, URI fileUri, String language) {
        Path basePath = workspace.getRootPath();
        for (DapServerConfig config : dapServerConfigs.values()) {
            if (config.canHandle(fileUri, language, basePath)) {
                if (workspace.getApplication().getDapServerConfig(config.getServerId()) == null) {
                    workspace.addDapServer(config);
                    LOG.infof("Added DAP server %s for language '%s' in workspace: %s",
                            config.getName(), language, workspace.getNormalizedUri());
                }
            }
        }
    }


    /**
     * Get workspace from a file URI by detecting the workspace root.
     * Walks up the directory tree to find pom.xml or build.gradle.
     * Ensures the appropriate LSP server is started for the file.
     */
    public CompletableFuture<Workspace> getWorkspaceForFile(URI fileUri,
                                                            ProgressMonitor progressMonitor) {
        URI rootUri = detectWorkspaceRoot(fileUri);
        if (rootUri == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Could not detect workspace root for: " + fileUri)
            );
        }

        Workspace workspace = getOrCreateWorkspace(rootUri);
        return ensureServerForFile(fileUri, workspace, progressMonitor)
                .thenApply(v -> workspace);
    }

    /**
     * Get or create workspace from a path (String cwd).
     * Converts the path to URI and creates/returns the workspace.
     *
     * @param cwd the workspace root path (e.g., "/home/user/project" or "C:\\Users\\project")
     * @return completable future with the workspace
     */
    public CompletableFuture<Workspace> getWorkspaceForPath(String cwd) {
        // Convert path to URI
        // If already a URI (starts with file:), use as-is
        // Otherwise convert path to file:/// URI (3 slashes for absolute paths)
        String workspaceUriStr;
        if (cwd.startsWith("file:")) {
            workspaceUriStr = cwd;
        } else {
            // Normalize path separators and create file URI
            String normalizedPath = cwd.replace("\\", "/");
            // Add leading slash if not present (for absolute paths)
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }
            workspaceUriStr = "file://" + normalizedPath;
        }

        URI workspaceUri = URI.create(workspaceUriStr);

        // Create workspace directly (no file detection needed)
        Workspace workspace = getOrCreateWorkspace(workspaceUri);
        return CompletableFuture.completedFuture(workspace);
    }

    /**
     * Shutdown all workspaces.
     */
    public CompletableFuture<Void> shutdownAll() {
        LOG.info("Shutting down all workspaces");

        CompletableFuture<?>[] shutdownFutures = workspaces.values().stream()
                .map(Workspace::shutdown)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(shutdownFutures)
                .thenRun(() -> {
                    workspaces.clear();
                    LOG.info("All workspaces shut down");
                });
    }

    /**
     * Detect workspace root by walking up to find pom.xml or build.gradle.
     */
    private URI detectWorkspaceRoot(URI fileUri) {
        try {
            Path path = Paths.get(fileUri);

            // If it's a file, start from its parent directory
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }

            // Walk up to find pom.xml or build.gradle
            while (path != null) {
                if (Files.exists(path.resolve("pom.xml")) ||
                        Files.exists(path.resolve("build.gradle")) ||
                        Files.exists(path.resolve("build.gradle.kts"))) {
                    return path.toUri();
                }
                path = path.getParent();
            }

            LOG.warnf("Could not find workspace root for: %s", fileUri);
            return null;

        } catch (Exception e) {
            LOG.error("Error detecting workspace root for: " + fileUri, e);
            return null;
        }
    }

    /**
     * Normalize URI (remove trailing slashes).
     */
    private URI normalizeUri(URI uri) {
        String uriStr = uri.toString();
        if (uriStr.endsWith("/")) {
            uriStr = uriStr.substring(0, uriStr.length() - 1);
        }
        return URI.create(uriStr);
    }

    public Workspace getWorkspace(URI uri) {
        return workspaces.get(uri);
    }

    /**
     * Get all active workspaces.
     */
    public Collection<Workspace> getWorkspaces() {
        return workspaces.values();
    }

    /**
     * Close a workspace: shutdown all its LSP servers and remove it from memory.
     */
    public CompletableFuture<Void> closeWorkspace(URI workspaceUri) {
        Workspace workspace = getWorkspace(workspaceUri);
        if (workspace == null) {
            LOG.warnf("Workspace not found: %s", workspaceUri);
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("Closing workspace: %s", workspaceUri);

        // Shutdown all servers in this workspace
        return workspace
                .shutdown()
                .thenRun(() -> {
                    // Remove from active workspaces
                    workspaces.remove(workspaceUri);
                    LOG.infof("Workspace closed and removed from memory: %s", workspaceUri);

                    // Fire workspace closed event
                    sendWorkspaceChangeEvent(WorkspaceChangeEvent.Type.CLOSED, workspaceUri);
                });
    }

    /**
     * Add server to workspace and start it (installation happens automatically in start()).
     */
    public CompletableFuture<Void> ensureServerStarted(String serverId, URI workspaceUri) {
        LspServerConfig config = getLspServerConfig(serverId);
        if (config == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown server: " + serverId));
        }

        Workspace workspace = getWorkspace(normalizeUri(workspaceUri));
        if (workspace == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Workspace not found: " + workspaceUri));
        }

        // Add server to workspace if not already present
        LspServer lspServer;
        if (!workspace.hasLspServer(serverId)) {
            lspServer = workspace.addLspServer(config);
        } else {
            lspServer = workspace.getLspServer(serverId);
        }

        // Start managed server with step-based progress (Installing → Starting → Initializing)
        String taskId = "start-" + serverId;
        String title = "Start " + serverId;
        TraceProgressMonitor progressMonitor = new TraceProgressMonitor(
                lspServer.getTraceCollector(), 100.0,
                progressBroadcasterInstance.isResolvable() ? progressBroadcasterInstance.get() : null,
                taskId, serverId, title);
        progressMonitor.addStep(ProgressStep.INSTALLING, 0.50);
        progressMonitor.addStep(ProgressStep.STARTING, 0.10);
        progressMonitor.addStep(ProgressStep.INITIALIZING, 0.40);
        progressMonitor.initializeSteps();

        return workspace.startManagedLspServer(serverId, progressMonitor)
                .exceptionally(ex -> {
                    LOG.errorf(ex, "Failed to start %s", config.getName());
                    progressMonitor.setFailed(ex.getMessage());
                    return null;
                });
    }

    public PathManager getPathManager() {
        return pathManager;
    }

    public Configuration getConfiguration() {
        return applicationConfiguration;
    }

    public List<WorkspaceConfigurationProvider> getWorkspaceConfigurationProviders() {
        List<String> ids = applicationConfiguration.getWorkspaceConfigurationProviderIds();
        WorkspaceConfigurationProviderRegistry registry = WorkspaceConfigurationProviderRegistry.getInstance();
        List<WorkspaceConfigurationProvider> providers = new ArrayList<>();
        for (String id : ids) {
            WorkspaceConfigurationProvider provider = registry.getProvider(id);
            if (provider != null) {
                providers.add(provider);
            }
        }
        return providers;
    }

    public WorkspaceConfigurationStrategy getWorkspaceConfigurationStrategy() {
        return applicationConfiguration.getWorkspaceConfigurationStrategy();
    }

    public ServerTrace getLspTraceLevel(String serverId) {
        return applicationConfiguration.getLspTraceLevel(serverId);
    }

    public ServerTrace getDapTraceLevel(String serverId) {
        return applicationConfiguration.getDapTraceLevel(serverId);
    }

    // LSP servers

    public LspServerConfig getLspServerConfig(String serverId) {
        return lspServerConfigs.get(serverId);
    }

    /**
     * Get all LSP server configuration
     *
     * @return all LSP server configuration
     */
    public Collection<LspServerConfig> getLspServerConfigs() {
        return lspServerConfigs.values();
    }

    public TraceCollector getLspTraceCollector() {
        return lspTraceCollector;
    }

    // DAP servers

    public DapServerConfig getDapServerConfig(String serverId) {
        return dapServerConfigs.get(serverId);
    }

    /**
     * Get all LSP server configuration
     *
     * @return all LSP server configuration
     */
    public Collection<DapServerConfig> getDapServerConfigs() {
        return dapServerConfigs.values();
    }

    public TraceCollector getDapTraceCollector() {
        return dapTraceCollector;
    }

    public McpTraceCollector getMcpTraceCollector() {
        return mcpTraceCollector;
    }

    private void sendWorkspaceChangeEvent(WorkspaceChangeEvent.Type type, URI workspaceUri) {
        workspaceChangeEvent.fire(new WorkspaceChangeEvent(type, workspaceUri));
    }

    public ContributionManager getContributionManager() {
        return contributionManager;
    }
}

