package com.redhat.mcp.languagetools.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads LSP server descriptors from JSON files.
 * Each server has: server.json (config) + installer.json (installation steps).
 * Similar to lsp4ij ServerDescriptor + InstallerDescriptor.
 */
@ApplicationScoped
public class ServerDescriptorLoader {

    private static final Logger LOG = Logger.getLogger(ServerDescriptorLoader.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load a bundled server configuration from resources.
     * Expects structure: /lsp/{serverId}/server.json and optionally /lsp/{serverId}/installer.json
     */
    public LspServerConfig loadBundled(String serverId) throws IOException {
        String serverPath = "/lsp/" + serverId + "/server.json";
        String installerPath = "/lsp/" + serverId + "/installer.json";

        LOG.infof("Loading bundled server config: %s", serverPath);

        // Load server.json
        LspServerConfig config;
        try (InputStream is = getClass().getResourceAsStream(serverPath)) {
            if (is == null) {
                throw new IOException("Bundled server config not found: " + serverPath);
            }
            config = parseServerConfig(is, serverId);
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
        JsonNode root = objectMapper.readTree(is);
        return objectMapper.treeToValue(root, InstallerConfig.class);
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
     */
    public Map<String, LspServerConfig> loadAllBundled() {
        Map<String, LspServerConfig> configs = new HashMap<>();

        // Load bundled servers (we'll add auto-discovery later)
        String[] serverIds = {"jdtls", "lemminx"};

        for (String serverId : serverIds) {
            try {
                LspServerConfig config = loadBundled(serverId);
                configs.put(config.getId(), config);
                LOG.infof("Loaded bundled server descriptor: %s", config.getId());
            } catch (IOException e) {
                LOG.warnf("Failed to load bundled %s config: %s", serverId, e.getMessage());
            }
        }

        return configs;
    }

    /**
     * Parse server configuration from JSON.
     */
    private LspServerConfig parseServerConfig(InputStream is, String defaultId) throws IOException {
        JsonNode root = objectMapper.readTree(is);

        LspServerConfig config = new LspServerConfig();

        // Basic info
        config.setId(root.has("id") ? root.get("id").asText() : defaultId);
        config.setName(root.has("name") ? root.get("name").asText() : null);
        config.setDescription(root.has("description") ? root.get("description").asText() : null);

        // Command - can be String or Object (for OS-specific commands)
        if (root.has("command")) {
            JsonNode commandNode = root.get("command");
            if (commandNode.isTextual()) {
                // Simple string command
                config.setCommand(commandNode.asText());
            } else if (commandNode.isObject()) {
                // OS-specific commands: {windows: "...", default: "..."}
                Map<String, String> commandMap = new HashMap<>();
                commandNode.fields().forEachRemaining(entry -> {
                    commandMap.put(entry.getKey(), entry.getValue().asText());
                });
                config.setCommand(commandMap);
            }
        }

        // Args (if separate from command)
        if (root.has("args")) {
            List<String> args = new ArrayList<>();
            root.get("args").forEach(arg -> args.add(arg.asText()));
            config.setArgs(args);
        }

        // Document selector
        if (root.has("documentSelector")) {
            List<DocumentSelector> selectors = new ArrayList<>();
            root.get("documentSelector").forEach(selectorNode -> {
                DocumentSelector selector = new DocumentSelector();
                if (selectorNode.has("language")) {
                    selector.setLanguage(selectorNode.get("language").asText());
                }
                if (selectorNode.has("scheme")) {
                    selector.setScheme(selectorNode.get("scheme").asText());
                }
                if (selectorNode.has("pattern")) {
                    selector.setPattern(selectorNode.get("pattern").asText());
                }
                selectors.add(selector);
            });
            config.setDocumentSelector(selectors);
        }

        // Installer (deprecated in server.json, should be in separate installer.json)
        if (root.has("installer")) {
            LOG.warnf("Installer config in server.json is deprecated, use separate installer.json for: %s", config.getId());
            InstallerConfig installer = objectMapper.treeToValue(root.get("installer"), InstallerConfig.class);
            config.setInstaller(installer);
        }

        // Environment variables
        if (root.has("env")) {
            Map<String, String> env = new HashMap<>();
            root.get("env").fields().forEachRemaining(entry ->
                env.put(entry.getKey(), entry.getValue().asText())
            );
            config.setEnv(env);
        }

        // Working directory
        if (root.has("workingDirectory")) {
            config.setWorkingDirectory(root.get("workingDirectory").asText());
        }

        // Initialization options
        if (root.has("initializationOptions")) {
            Map<String, Object> initOptions = objectMapper.convertValue(
                root.get("initializationOptions"),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            config.setInitializationOptions(initOptions);
        }

        return config;
    }
}
