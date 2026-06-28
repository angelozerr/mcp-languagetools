package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import com.redhat.mcp.languagetools.lsp.installer.InstallerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAP (Debug Adapter Protocol) server configuration loaded from server.json.
 * Similar to LspServerConfig but with DAP-specific fields.
 */
public class DapServerConfig {

    private String id;
    private String name;
    private String description;
    private Map<String, String> launch;  // OS-specific launch commands
    private Map<String, Object> attach;  // Attach configuration
    private String debugServerReadyPattern;
    private List<DocumentSelector> documentSelector = new ArrayList<>();
    private Map<String, Object> env = new HashMap<>();
    private String workingDirectory;
    private InstallerConfig installer;

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public Map<String, String> getLaunch() {
        return launch;
    }

    public void setLaunch(Map<String, String> launch) {
        this.launch = launch;
    }

    /**
     * Get the launch command for the current OS.
     */
    public String getLaunchForCurrentOS() {
        if (launch == null) {
            return null;
        }
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win") && launch.containsKey("windows")) {
            return launch.get("windows");
        } else if (os.contains("mac") && launch.containsKey("mac")) {
            return launch.get("mac");
        } else if (launch.containsKey("default")) {
            return launch.get("default");
        }
        return null;
    }

    public Map<String, Object> getAttach() {
        return attach;
    }

    public void setAttach(Map<String, Object> attach) {
        this.attach = attach;
    }

    public String getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern = debugServerReadyPattern;
    }

    public List<DocumentSelector> getDocumentSelector() {
        return documentSelector;
    }

    public void setDocumentSelector(List<DocumentSelector> documentSelector) {
        this.documentSelector = documentSelector;
    }

    public Map<String, Object> getEnv() {
        return env;
    }

    public void setEnv(Map<String, Object> env) {
        this.env = env;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public InstallerConfig getInstaller() {
        return installer;
    }

    public void setInstaller(InstallerConfig installer) {
        this.installer = installer;
    }
}
