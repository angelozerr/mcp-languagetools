package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.config.PathConfig;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.server.ServerConfigBase;

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

    private String parentServerId;

    public String getParentServerId() {
        return parentServerId;
    }

    public void setParentServerId(String parentServerId) {
        this.parentServerId = parentServerId;
    }

    public LspServerConfig(String serverId, PathManager pathManager) {
        super(serverId, pathManager.getLspServerHome(serverId));
    }

    /**
     * Check if this is a contribution-only config (no command, only contributes to other servers).
     */
    public boolean isContributionOnly() {
        return command == null && getContributes() != null;
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
            Map<String, String> commandMap = (Map<String, String>) command;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                return commandMap.getOrDefault("windows", commandMap.get("default"));
            } else if (os.contains("mac")) {
                return commandMap.getOrDefault("mac", commandMap.get("default"));
            } else if (os.contains("linux")) {
                return commandMap.getOrDefault("linux", commandMap.get("default"));
            }
            return commandMap.get("default");
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

    public boolean isExtension() {
        return parentServerId != null;
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
