package com.redhat.mcp.languagetools.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for loading server descriptors (LSP and DAP).
 * Each server directory contains: server.json + optional installer.json.
 * Uses Gson exclusively for JSON parsing.
 *
 * @param <T> Server config type (LspServerConfig or DapServerConfig)
 */
public abstract class ServerDescriptorLoaderBase<T extends ServerConfigBase> {

    private static final Logger LOG = Logger.getLogger(ServerDescriptorLoaderBase.class);

    // Json file names
    private static final String SERVER_CONFIG_FILE = "server.json";
    private static final String INSTALLER_CONFIG_FILE = "installer.json";

    // JSON field names
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_DOCUMENT_SELECTOR = "documentSelector";
    private static final String FIELD_LANGUAGE = "language";
    private static final String FIELD_SCHEME = "scheme";
    private static final String FIELD_PATTERN = "pattern";
    private static final String FIELD_CONTRIBUTES = "contributes";
    private static final String FIELD_TOOL_REQUESTS = "toolRequests";

    protected final Gson gson = new Gson();

    protected ServerDescriptorLoaderBase() {
    }

    /**
     * Get the root directory name for this server type (e.g., "lsp", "dap").
     */
    public abstract String getRoot();

    /**
     * Create a new instance of the config.
     */
    protected abstract T createConfig(String serverId, PathManager pathManager);

    /**
     * Load a bundled server configuration from a directory.
     * The directory can be a JAR entry or filesystem path.
     *
     * @param serverDir Directory containing server.json and installer.json
     * @return Loaded server configuration
     */
    public final T loadBundled(Path serverDir, PathManager pathManager) throws IOException {
        String serverId = serverDir.getFileName().toString();

        T config = createConfig(serverId, pathManager);
        loadConfig(serverId, serverDir, config);
        return config;
    }

    protected void loadConfig(String serverId, Path serverDir, T config) throws IOException {
        // Load server.json
        loadServer(serverId, serverDir, config);
        // Load installer.json
        try {
            loadInstaller(serverId, serverDir, config);
            LOG.debugf("Loaded installer config for: %s", config.getServerId());
        } catch (IOException e) {
            LOG.debugf("Failed to load installer config for %s: %s", config.getServerId(), e.getMessage());
        }
    }

    /**
     * Load server configuration from server.json.
     * Parses common fields + server-specific fields.
     */
    protected JsonObject loadServer(String serverId, Path serverDir, T config) throws IOException {
        Path serverFile = serverDir.resolve(SERVER_CONFIG_FILE);
        if (!Files.exists(serverFile)) {
            throw new IOException(SERVER_CONFIG_FILE + " is required");
        }
        return loadServerFromFile(serverId, serverFile, config);
    }

    /**
     * Load server configuration from server.json.
     * Parses common fields + server-specific fields.
     */
    protected JsonObject loadServerFromFile(String serverId, Path serverFile, T config) throws IOException {
        JsonObject jsonObject = loadJson(serverFile);

        // Common fields
        config.setName(jsonObject.has(FIELD_NAME) ? jsonObject.get(FIELD_NAME).getAsString() : serverId);

        if (jsonObject.has(FIELD_DESCRIPTION)) {
            config.setDescription(jsonObject.get(FIELD_DESCRIPTION).getAsString());
        }

        // Document selector
        if (jsonObject.has(FIELD_DOCUMENT_SELECTOR)) {
            List<DocumentSelector> selectors = new ArrayList<>();
            jsonObject.getAsJsonArray(FIELD_DOCUMENT_SELECTOR).forEach(el -> {
                JsonObject selectorObj = el.getAsJsonObject();
                DocumentSelector selector = new DocumentSelector();
                if (selectorObj.has(FIELD_LANGUAGE)) {
                    selector.setLanguage(selectorObj.get(FIELD_LANGUAGE).getAsString());
                }
                if (selectorObj.has(FIELD_SCHEME)) {
                    selector.setScheme(selectorObj.get(FIELD_SCHEME).getAsString());
                }
                if (selectorObj.has(FIELD_PATTERN)) {
                    selector.setPattern(selectorObj.get(FIELD_PATTERN).getAsString());
                }
                selectors.add(selector);
            });
            config.setDocumentSelector(selectors);
        }

        // Contributions
        fillContributions(config, jsonObject);

        // Tool requests (custom LSP requests for MCP tools)
        if (jsonObject.has(FIELD_TOOL_REQUESTS)) {
            Map<String, String> toolRequests = new HashMap<>();
            jsonObject.getAsJsonObject(FIELD_TOOL_REQUESTS).entrySet().forEach(entry ->
                    toolRequests.put(entry.getKey(), entry.getValue().getAsString())
            );
            config.setToolRequests(toolRequests);
        }

        return jsonObject;
    }

    private void fillContributions(ServerConfigBase config, JsonObject jsonObject) {
        // Contributions (LSP-specific)
        JsonElement contributesEl = jsonObject.get(FIELD_CONTRIBUTES);
        if (contributesEl != null) {
            if (contributesEl.isJsonObject()) {
                Contributes contributes = new Contributes();
                Map<String, JsonElement> contributionsMap = new HashMap<>();
                contributesEl.getAsJsonObject().entrySet().forEach(entry -> contributionsMap.put(entry.getKey(), entry.getValue()));
                contributes.setContributions(contributionsMap);
                config.setContributes(contributes);
            }
        }
    }

    protected void loadInstaller(String serverId, Path serverDir, T config) throws IOException {
        Path installerFile = serverDir.resolve(INSTALLER_CONFIG_FILE);
        if (!Files.exists(installerFile)) {
            return;
        }

        JsonObject jsonObject = loadJson(installerFile);
        config.setInstallerConfig(jsonObject);
    }

    protected JsonObject loadJson(Path jsonFile) throws IOException {
        try (InputStream is = Files.newInputStream(jsonFile);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, JsonObject.class);
        }
    }

}
