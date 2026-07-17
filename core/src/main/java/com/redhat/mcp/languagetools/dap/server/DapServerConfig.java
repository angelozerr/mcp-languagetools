package com.redhat.mcp.languagetools.dap.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.server.ServerConfigBase;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

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

    private static final Logger LOG = Logger.getLogger(DapServerConfig.class);

    /**
     * Attach configuration for DAP servers.
     */
    private Map<String, Object> attach;
    private String debugServerReadyPattern;
    private DebugServerWaitStrategy debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
    private Integer connectTimeout = 500; // Default 500ms

    public DapServerConfig(String serverId, Path serverHome, Application application) {
        super(serverId, serverHome, application);
    }

    // Getters and setters (id, name, description, command, env, workingDirectory, installer, documentSelector, trace inherited from ServerConfigBase)

    public Map<String, Object> getAttach() {
        return attach;
    }

    public void setAttach(Map<String, Object> attach) {
        this.attach = attach;
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern = debugServerReadyPattern;
        if (debugServerReadyPattern != null && !debugServerReadyPattern.isEmpty()) {
            this.debugServerWaitStrategy = DebugServerWaitStrategy.TRACE;
        }
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

    /**
     * Get configuration templates (launch/attach snippets) for this DAP server.
     * Templates are loaded from /dap/{serverId}/*.json files in the classpath.
     *
     * @return list of templates, or empty list if none found
     */
    public List<DapConfigurationTemplate> getConfigurationTemplates() {
        List<DapConfigurationTemplate> templates = new ArrayList<>();
        String resourcePath = "/dap/" + getServerId();

        URL resourceUrl = getClass().getResource(resourcePath);
        if (resourceUrl == null) {
            LOG.debugf("No templates directory found for DAP server: %s", getServerId());
            return templates;
        }

        try {
            Path dirPath = resolveResourcePath(resourceUrl);
            loadTemplatesFromDirectory(dirPath, templates);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to list templates directory for DAP server %s: %s", getServerId(), e.getMessage());
            // Return partial results - individual template failures are already logged
        }

        return templates;
    }

    private Path resolveResourcePath(URL resourceUrl) throws Exception {
        try {
            URI uri = resourceUrl.toURI();
            if ("jar".equals(uri.getScheme())) {
                // Running from JAR - need FileSystem
                FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                return fs.getPath(resourceUrl.getPath());
            } else {
                // Running from filesystem (development)
                return Paths.get(uri);
            }
        } catch (Exception e) {
            throw new Exception("Failed to resolve resource path: " + resourceUrl, e);
        }
    }

    private void loadTemplatesFromDirectory(Path dirPath, List<DapConfigurationTemplate> templates) {
        try (Stream<Path> paths = Files.list(dirPath)) {
            paths.filter(this::isTemplateFile)
                 .forEach(path -> loadTemplate(path, templates));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to list templates in directory %s: %s", dirPath, e.getMessage());
            // Individual template load failures are already logged in loadTemplate()
        }
    }

    private boolean isTemplateFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".json") &&
               (fileName.startsWith("attach.") || fileName.startsWith("launch."));
    }

    private void loadTemplate(Path path, List<DapConfigurationTemplate> templates) {
        try {
            String content = Files.readString(path);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            String fileName = path.getFileName().toString();
            String name = fileName.replace(".json", "");
            String label = json.has("name") ? json.get("name").getAsString() : name;

            // Parse body as JSON object, not raw string
            @SuppressWarnings("unchecked")
            Map<String, Object> body = new Gson().fromJson(json, Map.class);

            templates.add(new DapConfigurationTemplate(name, label, body));

        } catch (Exception e) {
            LOG.warnf(e, "Failed to load template from %s: %s", path, e.getMessage());
        }
    }
}
