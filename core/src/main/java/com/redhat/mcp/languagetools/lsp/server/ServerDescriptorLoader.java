package com.redhat.mcp.languagetools.lsp.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.lsp.DocumentSelector;
import com.redhat.mcp.languagetools.lsp.installer.InstallerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * Loads LSP and DAP server descriptors from JSON files.
 * Each server has: server.json (config) + installer.json (installation steps).
 * Similar to lsp4ij ServerDescriptor + InstallerDescriptor.
 */
@ApplicationScoped
public class ServerDescriptorLoader {

    private static final Logger LOG = Logger.getLogger(ServerDescriptorLoader.class);
    private static final String LSP_RESOURCE_DIR = "lsp";
    private static final String DAP_RESOURCE_DIR = "dap";
    private static final String SERVER_CONFIG_FILE = "server.json";
    private static final String SERVER_EXTENSION_CONFIG_FILE = "server-extension.json";
    private static final String INSTALLER_CONFIG_FILE = "installer.json";

    @Inject
    PathManager pathManager;

    private final Gson gson = new Gson();

    /**
     * Load a bundled server configuration from resources.
     * Expects structure: /lsp/{serverId}/server.json (or server-extension.json) and optionally /lsp/{serverId}/installer.json
     */
    public LspServerConfig loadBundled(String serverId) throws IOException {
        // Try server.json first, then server-extension.json
        String serverPath = buildResourcePath(serverId, SERVER_CONFIG_FILE);
        String extensionPath = buildResourcePath(serverId, SERVER_EXTENSION_CONFIG_FILE);
        String installerPath = buildResourcePath(serverId, INSTALLER_CONFIG_FILE);

        // Load server.json or server-extension.json
        LspServerConfig config;
        InputStream serverStream = getClass().getResourceAsStream(serverPath);
        boolean isExtension = false;

        if (serverStream == null) {
            // Try server-extension.json
            serverStream = getClass().getResourceAsStream(extensionPath);
            if (serverStream == null) {
                throw new IOException("Bundled server config not found: " + serverPath + " or " + extensionPath);
            }
            isExtension = true;
            LOG.infof("Loading bundled extension config: %s", extensionPath);
        } else {
            LOG.infof("Loading bundled server config: %s", serverPath);
        }

        try (InputStream is = serverStream) {
            config = parseServerConfig(is, serverId);
            config.setExtension(isExtension);
        }

        // Load installer.json if present
        try (InputStream is = getClass().getResourceAsStream(installerPath)) {
            if (is != null) {
                InstallerConfig installer = parseInstallerConfig(is);
                config.setInstaller(installer);
                LOG.debugf("Loaded installer config for: %s", serverId);
            }
        } catch (IOException e) {
            LOG.debugf("No installer config for %s: %s", serverId, e.getMessage());
        }

        return config;
    }

    /**
     * Parse installer configuration from JSON.
     */
    private InstallerConfig parseInstallerConfig(InputStream is) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, InstallerConfig.class);
        }
    }

    /**
     * Load a user-defined server configuration from file system.
     */
    public LspServerConfig loadFromFile(Path configFile) throws IOException {
        LOG.infof("Loading server config from file: %s", configFile);

        try (InputStream is = Files.newInputStream(configFile)) {
            String serverId = configFile.getFileName().toString().replace(".json", "");
            return parseServerConfig(is, serverId);
        }
    }

    /**
     * Load all bundled server configurations.
     * Auto-discovers all server.json files in /lsp/ directory.
     */
    public Map<String, LspServerConfig> loadAllBundled() {
        Map<String, LspServerConfig> configs = new HashMap<>();

        try {
            // Use context ClassLoader to see all resources (including from extensions in multi-module)
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            // Get all /lsp/ directories from all JARs/modules in classpath
            java.util.Enumeration<java.net.URL> lspResources = classLoader.getResources(LSP_RESOURCE_DIR);

            if (!lspResources.hasMoreElements()) {
                LOG.warnf("No /%s directory found in classpath", LSP_RESOURCE_DIR);
                return configs;
            }

            // Process each /lsp directory found (can be from multiple JARs/modules)
            while (lspResources.hasMoreElements()) {
                java.net.URL lspDirUrl = lspResources.nextElement();
                LOG.debugf("Scanning /%s from: %s", LSP_RESOURCE_DIR, lspDirUrl);

                // Handle both JAR and file system paths
                java.net.URI lspDirUri = lspDirUrl.toURI();
                java.nio.file.Path lspPath;

                if (lspDirUri.getScheme().equals("jar")) {
                    // Running from JAR - use FileSystem
                    java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(lspDirUri, java.util.Collections.emptyMap());
                    lspPath = fs.getPath("/" + LSP_RESOURCE_DIR);
                } else {
                    // Running from IDE/filesystem
                    lspPath = java.nio.file.Paths.get(lspDirUri);
                }

                // Scan for server.json files in this /lsp directory
                try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(lspPath)) {
                    entries.filter(java.nio.file.Files::isDirectory)
                           .forEach(dir -> {
                               String serverId = dir.getFileName().toString();
                               // Skip if already loaded (first one wins)
                               if (configs.containsKey(serverId)) {
                                   LOG.debugf("Skipping duplicate server: %s", serverId);
                                   return;
                               }
                               try {
                                   LspServerConfig config = loadBundled(serverId);
                                   configs.put(config.getId(), config);
                                   LOG.infof("Auto-discovered bundled server: %s", config.getId());
                               } catch (IOException e) {
                                   LOG.debugf("Skipping %s (no valid server.json): %s", serverId, e.getMessage());
                               }
                           });
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to auto-discover bundled servers");
        }

        return configs;
    }

    /**
     * Parse server configuration from JSON.
     */
    private LspServerConfig parseServerConfig(InputStream is, String defaultId) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            LspServerConfig config = new LspServerConfig();

            // Basic info
            config.setId(root.has("id") ? root.get("id").getAsString() : defaultId);
            config.setName(root.has("name") ? root.get("name").getAsString() : null);
            config.setDescription(root.has("description") ? root.get("description").getAsString() : null);

            // Command - can be String or Object (for OS-specific commands)
            if (root.has("command")) {
                JsonElement commandNode = root.get("command");
                if (commandNode.isJsonPrimitive()) {
                    // Simple string command
                    config.setCommand(commandNode.getAsString());
                } else if (commandNode.isJsonObject()) {
                    // OS-specific commands: {windows: "...", default: "..."}
                    Map<String, String> commandMap = new HashMap<>();
                    commandNode.getAsJsonObject().entrySet().forEach(entry -> {
                        commandMap.put(entry.getKey(), entry.getValue().getAsString());
                    });
                    config.setCommand(commandMap);
                }
            }

            // Args (if separate from command)
            if (root.has("args")) {
                List<String> args = new ArrayList<>();
                root.getAsJsonArray("args").forEach(arg -> args.add(arg.getAsString()));
                config.setArgs(args);
            }

            // Document selector
            if (root.has("documentSelector")) {
                List<DocumentSelector> selectors = new ArrayList<>();
                root.getAsJsonArray("documentSelector").forEach(selectorEl -> {
                    JsonObject selectorNode = selectorEl.getAsJsonObject();
                    DocumentSelector selector = new DocumentSelector();
                    if (selectorNode.has("language")) {
                        selector.setLanguage(selectorNode.get("language").getAsString());
                    }
                    if (selectorNode.has("scheme")) {
                        selector.setScheme(selectorNode.get("scheme").getAsString());
                    }
                    if (selectorNode.has("pattern")) {
                        selector.setPattern(selectorNode.get("pattern").getAsString());
                    }
                    selectors.add(selector);
                });
                config.setDocumentSelector(selectors);
            }

            // Contributes (extensions)
            if (root.has("contributes")) {
                JsonElement contributesEl = root.get("contributes");
                if (contributesEl.isJsonObject()) {
                    // Parse the contributes structure: { "jdtls": {...}, "microprofile": {...} }
                    Contributes contributes = new Contributes();
                    Map<String, JsonElement> contributionsMap = new HashMap<>();
                    contributesEl.getAsJsonObject().entrySet().forEach(entry -> {
                        contributionsMap.put(entry.getKey(), entry.getValue());
                    });
                    contributes.setContributions(contributionsMap);
                    config.setContributes(contributes);
                }
            }

            // Installer (deprecated in server.json, should be in separate installer.json)
            if (root.has("installer")) {
                LOG.warnf("Installer config in server.json is deprecated, use separate installer.json for: %s", config.getId());
                InstallerConfig installer = gson.fromJson(root.get("installer"), InstallerConfig.class);
                config.setInstaller(installer);
            }

            // Environment variables
            if (root.has("env")) {
                Map<String, String> env = new HashMap<>();
                root.getAsJsonObject("env").entrySet().forEach(entry ->
                    env.put(entry.getKey(), entry.getValue().getAsString())
                );
                config.setEnv(env);
            }

            // Working directory
            if (root.has("workingDirectory")) {
                config.setWorkingDirectory(root.get("workingDirectory").getAsString());
            }

            // Initialization options
            if (root.has("initializationOptions")) {
                Map<String, Object> initOptions = gson.fromJson(
                    root.get("initializationOptions"),
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType()
                );
                config.setInitializationOptions(initOptions);
            }

            return config;
        }
    }

    /**
     * Build resource path for bundled server files.
     * @param serverId Server ID
     * @param fileName File name (e.g., "server.json", "installer.json")
     * @return Resource path (e.g., "/lsp/jdtls/server.json")
     */
    private String buildResourcePath(String serverId, String fileName) {
        return "/" + LSP_RESOURCE_DIR + "/" + serverId + "/" + fileName;
    }

    // ========== DAP Server Loading ==========

    /**
     * Load a bundled DAP server configuration from resources.
     * Expects structure: /dap/{serverId}/server.json and optionally /dap/{serverId}/installer.json
     */
    public DapServerConfig loadDapBundled(String serverId) throws IOException {
        String serverPath = buildDapResourcePath(serverId, SERVER_CONFIG_FILE);
        String installerPath = buildDapResourcePath(serverId, INSTALLER_CONFIG_FILE);

        // Load server.json
        DapServerConfig config;
        try (InputStream is = getClass().getResourceAsStream(serverPath)) {
            if (is == null) {
                throw new IOException("Bundled DAP server config not found: " + serverPath);
            }
            LOG.infof("Loading bundled DAP config: %s", serverPath);
            config = parseDapServerConfig(is, serverId);
        }

        // Load installer.json if present
        try (InputStream is = getClass().getResourceAsStream(installerPath)) {
            if (is != null) {
                InstallerConfig installer = parseInstallerConfig(is);
                config.setInstaller(installer);
                LOG.debugf("Loaded installer config for DAP: %s", serverId);
            }
        } catch (IOException e) {
            LOG.debugf("No installer config for DAP %s: %s", serverId, e.getMessage());
        }

        return config;
    }

    /**
     * Load all bundled DAP server configurations.
     * Auto-discovers all server.json files in /dap/ directory.
     */
    public Map<String, DapServerConfig> loadAllDapBundled() {
        Map<String, DapServerConfig> configs = new HashMap<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            java.util.Enumeration<java.net.URL> dapResources = classLoader.getResources(DAP_RESOURCE_DIR);

            if (!dapResources.hasMoreElements()) {
                LOG.debugf("No /%s directory found in classpath", DAP_RESOURCE_DIR);
                return configs;
            }

            while (dapResources.hasMoreElements()) {
                java.net.URL dapDirUrl = dapResources.nextElement();
                LOG.debugf("Scanning /%s from: %s", DAP_RESOURCE_DIR, dapDirUrl);

                java.net.URI dapDirUri = dapDirUrl.toURI();
                java.nio.file.Path dapPath;

                if (dapDirUri.getScheme().equals("jar")) {
                    java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(dapDirUri, java.util.Collections.emptyMap());
                    dapPath = fs.getPath("/" + DAP_RESOURCE_DIR);
                } else {
                    dapPath = java.nio.file.Paths.get(dapDirUri);
                }

                try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(dapPath)) {
                    entries.filter(java.nio.file.Files::isDirectory)
                           .forEach(dir -> {
                               String serverId = dir.getFileName().toString();
                               if (configs.containsKey(serverId)) {
                                   LOG.debugf("Skipping duplicate DAP server: %s", serverId);
                                   return;
                               }
                               try {
                                   DapServerConfig config = loadDapBundled(serverId);
                                   configs.put(config.getId(), config);
                                   LOG.infof("Auto-discovered bundled DAP server: %s", config.getId());
                               } catch (IOException e) {
                                   LOG.debugf("Skipping DAP %s (no valid server.json): %s", serverId, e.getMessage());
                               }
                           });
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to auto-discover bundled DAP servers");
        }

        return configs;
    }

    /**
     * Parse DAP server configuration from JSON.
     */
    private DapServerConfig parseDapServerConfig(InputStream is, String defaultId) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            DapServerConfig config = new DapServerConfig();

            // Basic info
            config.setId(root.has("id") ? root.get("id").getAsString() : defaultId);
            config.setName(root.has("name") ? root.get("name").getAsString() : null);
            config.setDescription(root.has("description") ? root.get("description").getAsString() : null);

            // Launch - can be String or Object (for OS-specific commands)
            if (root.has("launch")) {
                JsonElement launchNode = root.get("launch");
                Map<String, String> launchMap = new HashMap<>();
                if (launchNode.isJsonPrimitive()) {
                    launchMap.put("default", launchNode.getAsString());
                } else if (launchNode.isJsonObject()) {
                    launchNode.getAsJsonObject().entrySet().forEach(entry -> {
                        launchMap.put(entry.getKey(), entry.getValue().getAsString());
                    });
                }
                config.setLaunch(launchMap);
            }

            // Attach configuration (optional)
            if (root.has("attach")) {
                Map<String, Object> attachMap = gson.fromJson(
                    root.get("attach"),
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType()
                );
                config.setAttach(attachMap);
            }

            // Debug server ready pattern
            if (root.has("debugServerReadyPattern")) {
                config.setDebugServerReadyPattern(root.get("debugServerReadyPattern").getAsString());
            }

            // Document selector
            if (root.has("documentSelector")) {
                List<DocumentSelector> selectors = new ArrayList<>();
                root.getAsJsonArray("documentSelector").forEach(selectorEl -> {
                    JsonObject selectorNode = selectorEl.getAsJsonObject();
                    DocumentSelector selector = new DocumentSelector();
                    if (selectorNode.has("language")) {
                        selector.setLanguage(selectorNode.get("language").getAsString());
                    }
                    if (selectorNode.has("scheme")) {
                        selector.setScheme(selectorNode.get("scheme").getAsString());
                    }
                    if (selectorNode.has("pattern")) {
                        selector.setPattern(selectorNode.get("pattern").getAsString());
                    }
                    selectors.add(selector);
                });
                config.setDocumentSelector(selectors);
            }

            // Environment variables
            if (root.has("env")) {
                Map<String, Object> env = new HashMap<>();
                root.getAsJsonObject("env").entrySet().forEach(entry ->
                    env.put(entry.getKey(), entry.getValue().getAsString())
                );
                config.setEnv(env);
            }

            // Working directory
            if (root.has("workingDirectory")) {
                config.setWorkingDirectory(root.get("workingDirectory").getAsString());
            }

            return config;
        }
    }

    /**
     * Build resource path for bundled DAP server files.
     * @param serverId Server ID
     * @param fileName File name (e.g., "server.json", "installer.json")
     * @return Resource path (e.g., "/dap/vscode-js-debug/server.json")
     */
    private String buildDapResourcePath(String serverId, String fileName) {
        return "/" + DAP_RESOURCE_DIR + "/" + serverId + "/" + fileName;
    }
}
