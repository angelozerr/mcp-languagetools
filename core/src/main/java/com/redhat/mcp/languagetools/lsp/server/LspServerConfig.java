package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.server.ServerConfigBase;
import com.redhat.mcp.languagetools.utils.OSUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a language server.
 * Can be loaded from JSON or built programmatically.
 */
public class LspServerConfig extends ServerConfigBase {

    /**
     * Command to execute the language server (simple string or OS-specific map)
     * Can be either:
     * - A simple command string (used for all OS)
     * - A map with "windows", "linux", "mac", "default" keys for OS-specific commands
     */
    private Object command;

    /**
     * Environment variables
     */
    private Map<String, String> env = new HashMap<>();

    /**
     * Working directory for the server process
     */
    private String workingDirectory;

    /**
     * Server initialization options
     */
    private Map<String, Object> initializationOptions = new HashMap<>();

    /**
     * Whether to skip sending didOpen before position-based requests (references, definition, etc.).
     * Defaults to false (didOpen is sent). Set to true for servers that index the whole project (e.g. JDTLS, pyright).
     */
    private boolean skipDidOpen;

    public LspServerConfig(String serverId, Application application) {
        super(serverId, application.getPathManager().getLspServerHome(serverId), application);
    }

    /**
     * Check if this is a contribution-only config (no command, only contributes to other servers).
     */
    public boolean isContributionOnly() {
        return command == null && getContributes() != null;
    }

    /**
     * Detect parent server ID from contributes configuration.
     * For contribution-only configs (like Quarkus), the parent is the server
     * they contribute classpath JARs to (e.g., microprofile).
     *
     * @return parent server ID, or null if no parent
     */
    public String getParentServerId() {
        var contributes = getContributes();
        if (contributes == null || contributes.getContributions() == null || contributes.getContributions().isEmpty()) {
            return null;
        }

        // Find the contribution with classpath - that's the parent server
        return contributes.getContributions().entrySet().stream()
            .filter(entry -> {
                var contribution = entry.getValue();
                if (!contribution.isJsonObject()) {
                    return false;
                }
                var obj = contribution.getAsJsonObject();
                return obj.has(ClasspathExtensibleContributes.CLASSPATH)
                    && obj.get(ClasspathExtensibleContributes.CLASSPATH).isJsonArray();
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    @Override
    protected void onCommandInstalled(String command) {
        if (this.command == null) {
            this.command = command;
        }
    }

    // Getters and setters (id, name, description, installer inherited from ServerConfigBase)

    public Object getCommand() {
        return command;
    }

    public void setCommand(Object command) {
        this.command = command;
    }

    /**
     * Get the command for the current OS.
     */
    @SuppressWarnings("unchecked")
    public String getCommandForCurrentOS() {
        if (command instanceof String) {
            return (String) command;
        }
        if (command instanceof Map) {
            return OSUtils.getStringFromOs((Map<String, String>) command);
        }
        return null;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Map<String, Object> getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(Map<String, Object> initializationOptions) {
        this.initializationOptions = initializationOptions;
    }

    public boolean isSkipDidOpen(LspCapability capability) {
        return skipDidOpen;
    }

    public void setSkipDidOpen(boolean skipDidOpen) {
        this.skipDidOpen = skipDidOpen;
    }

    @Override
    public String toString() {
        return "LspServerConfig{" +
                "id='" + getServerId() + '\'' +
                ", name='" + name + '\'' +
                ", command='" + command + '\'' +
                ", documentSelector=" + getDocumentSelector() +
                '}';
    }

}
