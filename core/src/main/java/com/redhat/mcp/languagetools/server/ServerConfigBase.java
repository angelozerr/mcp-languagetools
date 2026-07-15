package com.redhat.mcp.languagetools.server;

import com.google.gson.JsonElement;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.installer.*;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.progress.SharedProgressMonitor;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Base class for server configurations (LSP and DAP).
 * Contains common fields: id, name, description, installer, documentSelector.
 */
public abstract class ServerConfigBase implements ServerConfig {

    private static final Logger LOG = Logger.getLogger(ServerConfigBase.class);

    private final String serverId;
    private final Path serverHome;
    protected String name;
    protected String description;
    protected JsonElement installerConfig;  // Raw JSON from installer.json
    protected List<DocumentSelector> documentSelector = new ArrayList<>();

    /**
     * Contributions (VS Code-like extension system)
     */
    private Contributes contributes;

    // Trace collector (set by workspace/session when server is added)
    protected TraceCollector traceCollector;

    // Lazy-loaded installer instance
    private ServerInstaller installer;

    // Install progress monitor (set when installation starts)
    private TraceProgressMonitor installProgress;

    // Shared progress monitor for installation (allows multiple listeners)
    private volatile SharedProgressMonitor sharedInstallProgress;

    // Installation state - shared across all workspaces
    private volatile CompletableFuture<InstallResult> installationFuture;

    public ServerConfigBase(String serverId, Path serverHome) {
        this.serverId = serverId;
        this.serverHome = serverHome;
    }

    // Common getters

    @Override
    public String getServerId() {
        return serverId;
    }

    @Override
    public Path getServerHome() {
        return serverHome;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public JsonElement getInstallerConfig() {
        return installerConfig;
    }

    public void setInstallerConfig(JsonElement installerConfig) {
        this.installerConfig = installerConfig;
    }

    /**
     * Gets the installer instance (lazy-loaded).
     * Returns null if no installer configuration is present.
     */
    public ServerInstaller getInstaller() {
        if (installer == null && installerConfig != null) {
            installer = createInstaller();
        }
        return installer;
    }

    /**
     * Creates the installer instance from configuration.
     * Override this method to use a different installer implementation.
     */
    protected ServerInstaller createInstaller() {
        if (installerConfig == null) {
            return null;
        }
        return new TaskRegistryInstaller(this);
    }

    /**
     * Gets the trace collector for this server.
     */
    public TraceCollector getTraceCollector() {
        return traceCollector;
    }

    /**
     * Sets the trace collector for this server.
     */
    public void setTraceCollector(TraceCollector traceCollector) {
        this.traceCollector = traceCollector;
    }

    public List<DocumentSelector> getDocumentSelector() {
        return documentSelector;
    }

    public void setDocumentSelector(List<DocumentSelector> documentSelector) {
        this.documentSelector = documentSelector;
    }

    public Contributes getContributes() {
        return contributes;
    }

    public void setContributes(Contributes contributes) {
        this.contributes = contributes;
    }

    /**
     * Check if this server can handle the given file.
     */
    public boolean canHandle(String uri, String language) {
        return getDocumentSelector().stream()
                .anyMatch(selector -> selector.matches(uri, language));
    }

    /**
     * Get the resource base path for this server in the classpath.
     * For example: "/lsp/quarkus" for quarkus, "/dap/vscode-js-debug" for vscode-js-debug.
     * Derived from serverHome path structure.
     */
    public String getResourceBasePath() {
        // serverHome is like: /.../lsp/quarkus or /.../dap/vscode-js-debug
        // We extract the last 2 segments: lsp/quarkus or dap/vscode-js-debug
        Path parent = serverHome.getParent();  // lsp or dap
        if (parent != null) {
            Path grandParent = parent.getParent();
            if (grandParent != null) {
                return "/" + parent.getFileName() + "/" + serverHome.getFileName();
            }
        }
        // Fallback
        return "/lsp/" + serverId;
    }

    /**
     * Gets the install progress monitor (used to show visual progress bar in UI).
     */
    public TraceProgressMonitor getInstallProgress() {
        return installProgress;
    }

    /**
     * Gets the shared install progress monitor (used for cancellation from Admin UI).
     * Returns null if no installation is in progress.
     */
    public SharedProgressMonitor getSharedInstallProgress() {
        return sharedInstallProgress;
    }

    /**
     * Sets the install progress monitor (called when installation starts).
     */
    public void setInstallProgress(TraceProgressMonitor installProgress) {
        this.installProgress = installProgress;
    }

    /**
     * Reset installation state so the next ensureInstalled call starts fresh.
     * Called from admin UI endpoints when the user explicitly requests an install.
     */
    public void resetInstallState() {
        synchronized (this) {
            CompletableFuture<InstallResult> future = installationFuture;
            if (future != null && future.isDone()) {
                installationFuture = null;
            }
        }
    }

    /**
     * Ensure server is installed.
     * This method is thread-safe - only one installation will run even if called from multiple workspaces.
     * Returns a CompletableFuture that completes when installation is done.
     * If installation fails, the future is reset to null to allow retry.
     *
     * @param pathManager Path manager
     * @param serverStatusCallback Status callback
     * @param progressMonitor Progress monitor (never null, use ProgressMonitor.none() if not available)
     */
    public CompletableFuture<InstallResult> ensureInstalled(PathManager pathManager,
                                                            Consumer<ServerStatus> serverStatusCallback,
                                                            ProgressMonitor progressMonitor) {
        return ensureInstalled(pathManager, serverStatusCallback, progressMonitor, false);
    }

    public CompletableFuture<InstallResult> ensureInstalled(PathManager pathManager,
                                                            Consumer<ServerStatus> serverStatusCallback,
                                                            ProgressMonitor progressMonitor,
                                                            boolean force) {
        // progressMonitor must never be null - use ProgressMonitor.none() instead
        // If null, let it fail with NullPointerException to catch bugs early

        ServerInstaller installer = getInstaller();
        if (installer == null) {
            LOG.warnf("No installer for server '%s' (installerConfig=%s)", serverId, installerConfig != null ? "present" : "NULL");
            return CompletableFuture.completedFuture(null);
        }
        LOG.infof("ensureInstalled called for '%s', force=%s, installationFuture=%s",
                serverId, force, installationFuture != null ? (installationFuture.isDone() ? "done" : "running") : "null");

        // Force install: reset previous installation state
        if (force) {
            synchronized (this) {
                installationFuture = null;
            }
        }

        // Double-checked locking pattern
        CompletableFuture<InstallResult> future = installationFuture;
        if (future == null) {
            synchronized (this) {
                future = installationFuture;
                if (future == null) {
                    // FIRST caller - create SharedProgressMonitor for this installation
                    sharedInstallProgress = new SharedProgressMonitor();

                    // Create task ID for this installation
                    String taskId = "install-" + serverId;
                    sharedInstallProgress.startTask(taskId);

                    // Add TraceProgressMonitor for Admin UI
                    TraceProgressMonitor traceProgress = new TraceProgressMonitor(traceCollector);
                    setInstallProgress(traceProgress);
                    sharedInstallProgress.addListener(traceProgress);

                    // Add progress monitor from parameter (never null)
                    if (progressMonitor != ProgressMonitor.none()) {
                        sharedInstallProgress.addListener(progressMonitor);
                    }

                    // Map InstallationStatus to ServerStatus
                    Consumer<InstallationStatus> installStatusCallback = installStatus -> {
                        ServerStatus serverStatus = switch (installStatus) {
                            case INSTALLING -> ServerStatus.INSTALLING;
                            case FAILED -> ServerStatus.INSTALL_FAILED;
                            default -> null;
                        };
                        if (serverStatus != null && serverStatusCallback != null) {
                            serverStatusCallback.accept(serverStatus);
                        }
                    };

                    InstallerContext context = new InstallerContext(this, sharedInstallProgress, installStatusCallback);
                    context.setVariable("USER_HOME", pathManager.getMcpLangToolsRoot().toString());
                    context.setForceInstall(force);

                    future = installer.ensureInstalled(context)
                            .whenComplete((result, error) -> {
                                sharedInstallProgress.endTask(taskId);
                                sharedInstallProgress = null;

                                // Reset on failure to allow retry
                                if (error != null) {
                                    synchronized (this) {
                                        installationFuture = null;
                                    }
                                }
                            });
                    installationFuture = future;
                }
            }
        } else if (sharedInstallProgress != null) {
            // SUBSEQUENT callers - register as listener to get installation progress
            if (progressMonitor != ProgressMonitor.none()) {
                sharedInstallProgress.addListener(progressMonitor);
            }
        }
        return future;
    }

}
