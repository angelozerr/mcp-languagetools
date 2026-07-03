package com.redhat.mcp.languagetools.server;

import com.google.gson.JsonElement;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.installer.*;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import com.redhat.mcp.languagetools.trace.TraceCollector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Base class for server configurations (LSP and DAP).
 * Contains common fields: id, name, description, installer, documentSelector.
 */
public abstract class ServerConfigBase implements ServerConfig {

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

    // Install progress indicator (set when installation starts)
    private TraceProgressIndicator installProgress;

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
     * Gets the install progress indicator (used to show visual progress bar in UI).
     */
    public TraceProgressIndicator getInstallProgress() {
        return installProgress;
    }

    /**
     * Sets the install progress indicator (called when installation starts).
     */
    public void setInstallProgress(TraceProgressIndicator installProgress) {
        this.installProgress = installProgress;
    }

    /**
     * Ensure server is installed.
     * This method is thread-safe - only one installation will run even if called from multiple workspaces.
     * Returns a CompletableFuture that completes when installation is done.
     * If installation fails, the future is reset to null to allow retry.
     */
    public CompletableFuture<InstallResult> ensureInstalled(PathManager pathManager,
                                                            Consumer<ServerStatus> serverStatusCallback) {
        ServerInstaller installer = getInstaller();
        if (installer == null) {
            // No installer - server is assumed to be already available
            return CompletableFuture.completedFuture(null);
        }

        // Double-checked locking pattern
        if (installationFuture == null) {
            synchronized (this) {
                if (installationFuture == null) {
                    // Create and start installation
                    TraceProgressIndicator progress = new TraceProgressIndicator(traceCollector);
                    setInstallProgress(progress);

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

                    InstallerContext context = new InstallerContext(this, progress, installStatusCallback);
                    context.setVariable("USER_HOME", pathManager.getMcpLangToolsRoot().toString());

                    installationFuture = installer.ensureInstalled(context)
                            .whenComplete((result, error) -> {
                                // Reset on failure to allow retry
                                if (error != null) {
                                    synchronized (this) {
                                        installationFuture = null;
                                    }
                                }
                            });
                }
            }
        }
        return installationFuture;
    }

}
