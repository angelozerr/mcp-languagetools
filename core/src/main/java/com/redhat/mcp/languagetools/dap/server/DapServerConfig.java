package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.dap.transport.TransportType;
import com.redhat.mcp.languagetools.server.ServerConfigBase;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * DAP (Debug Adapter Protocol) server configuration loaded from server.json.
 * Similar to LspServerConfig but with DAP-specific fields.
 *
 * <p>DAP servers can be launched in two modes:</p>
 * <ul>
 *   <li><b>Standalone mode</b>: Uses {@code launch} to start an external process (e.g., vscode-js-debug)</li>
 *   <li><b>Embedded mode</b>: Uses {@code launchMethod} to call an LSP method that returns a DAP port (e.g., java-debug via JDTLS)</li>
 * </ul>
 */
public class DapServerConfig extends ServerConfigBase {

    /**
     * OS-specific launch commands for standalone DAP servers.
     * Example: {"default": "node debugAdapter.js ${port}"}
     */
    private Map<String, String> launch;

    /**
     * Attach configuration for DAP servers.
     */
    private Map<String, Object> attach;
    private String debugServerReadyPattern;
    private DebugServerWaitStrategy debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
    private Integer connectTimeout = 500; // Default 500ms
    private TransportType transport = TransportType.STDIO; // Default to stdio
    private Map<String, Object> env = new HashMap<>();
    private String workingDirectory;

    public DapServerConfig(String serverId, Path serverHome) {
        super(serverId, serverHome);
    }

    // Getters and setters (id, name, description, installer, documentSelector, trace inherited from ServerConfigBase)

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

    public DebugServerWaitStrategy getDebugServerWaitStrategy() {
        return debugServerWaitStrategy;
    }

    public void setDebugServerWaitStrategy(DebugServerWaitStrategy debugServerWaitStrategy) {
        this.debugServerWaitStrategy = debugServerWaitStrategy;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Get the ServerReadyConfig based on the wait strategy.
     */
    public ServerReadyConfig getServerReadyConfig() {
        return switch (debugServerWaitStrategy) {
            case TIMEOUT -> new ServerReadyConfig(connectTimeout != null ? connectTimeout : 500);
            case TRACE -> debugServerReadyPattern != null
                ? new ServerReadyConfig(debugServerReadyPattern)
                : new ServerReadyConfig(500);
        };
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

    public TransportType getTransport() {
        return transport;
    }

    public void setTransport(TransportType transport) {
        this.transport = transport;
    }

    /**
     * Get configuration templates (launch/attach snippets) for this DAP server.
     * Templates are loaded from /dap/{serverId}/*.json in the classpath.
     * Returns a list of template objects with "name", "label", and "body" fields.
     */
    public java.util.List<Map<String, Object>> getConfigurationTemplates() {
        java.util.List<Map<String, Object>> templates = new java.util.ArrayList<>();

        try {
            // List all JSON files in /dap/{serverId}/ directory
            String resourcePath = "/dap/" + getServerId();
            java.net.URL resourceUrl = getClass().getResource(resourcePath);

            if (resourceUrl == null) {
                return templates; // No templates directory for this server
            }

            // Read files from JAR or filesystem
            java.nio.file.Path dirPath;
            if (resourceUrl.toURI().getScheme().equals("jar")) {
                // Running from JAR
                java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(
                    resourceUrl.toURI(),
                    java.util.Collections.emptyMap()
                );
                dirPath = fs.getPath(resourcePath);
            } else {
                // Running from filesystem (development)
                dirPath = java.nio.file.Paths.get(resourceUrl.toURI());
            }

            // List all .json files that start with "attach." or "launch."
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.list(dirPath)) {
                paths.filter(path -> {
                         String fileName = path.getFileName().toString();
                         return fileName.endsWith(".json") &&
                                (fileName.startsWith("attach.") || fileName.startsWith("launch."));
                     })
                     .forEach(path -> {
                         try {
                             String content = java.nio.file.Files.readString(path);
                             com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                             // Extract template info
                             String fileName = path.getFileName().toString();
                             String name = fileName.replace(".json", "");
                             String label = json.has("name") ? json.get("name").getAsString() : name;

                             Map<String, Object> template = new java.util.HashMap<>();
                             template.put("name", name);
                             template.put("label", label);
                             template.put("body", content);

                             templates.add(template);
                         } catch (Exception e) {
                             // Skip invalid template files
                         }
                     });
            }
        } catch (Exception e) {
            // No templates available or error reading them
        }

        return templates;
    }
}
