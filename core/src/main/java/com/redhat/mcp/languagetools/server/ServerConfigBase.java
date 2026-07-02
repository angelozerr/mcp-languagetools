package com.redhat.mcp.languagetools.server;

import com.google.gson.JsonElement;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.installer.ServerConfig;
import com.redhat.mcp.languagetools.installer.ServerInstaller;
import com.redhat.mcp.languagetools.installer.TaskRegistryInstaller;
import com.redhat.mcp.languagetools.installer.TraceProgressIndicator;
import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import com.redhat.mcp.languagetools.trace.TraceCollector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    // Trace collector (set by workspace/session when server is added)
    protected TraceCollector traceCollector;

    // Lazy-loaded installer instance
    private ServerInstaller installer;

    // Install progress indicator (set when installation starts)
    private TraceProgressIndicator installProgress;

    public ServerConfigBase(String serverId, Path serverHome) {
        this.serverId = serverId;
        this.serverHome =serverHome;
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

    /**
     * Check if this server can handle the given file.
     */
    public boolean canHandle(String uri, String language) {
        return getDocumentSelector().stream()
                .anyMatch(selector -> selector.matches(uri, language));
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

}
