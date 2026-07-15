package com.redhat.mcp.languagetools.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.redhat.mcp.languagetools.PathManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application settings stored in ~/.mcp-languagetools/settings.json
 * with a flat key-value format (e.g. "lsp.microprofile.trace": "messages").
 */
@ApplicationScoped
public class Settings {

    private static final Logger LOG = Logger.getLogger(Settings.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Inject
    PathManager pathManager;

    private Map<String, String> settings;

    private final Map<String, ServerTrace> lspTraceLevels = new ConcurrentHashMap<>();
    private final Map<String, ServerTrace> dapTraceLevels = new ConcurrentHashMap<>();
    private volatile ServerTrace mcpTraceLevel;

    @PostConstruct
    void init() {
        load();
    }

    private Path getSettingsFile() {
        return pathManager.getSettingsFile();
    }

    private void load() {
        Path settingsFile = getSettingsFile();
        if (Files.exists(settingsFile)) {
            loadFromFile(settingsFile);
            return;
        }

        LOG.infof("No settings found at %s, using defaults", settingsFile);
        settings = new LinkedHashMap<>();
    }

    private void loadFromFile(Path file) {
        try {
            String json = Files.readString(file);
            TypeToken<Map<String, String>> typeToken = new TypeToken<>() {};
            settings = GSON.fromJson(json, typeToken.getType());
            if (settings == null) {
                settings = new LinkedHashMap<>();
            }
            LOG.infof("Loaded settings from %s", file);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to load settings from %s", file);
            settings = new LinkedHashMap<>();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(getSettingsFile().getParent());
            String json = GSON.toJson(settings);
            Files.writeString(getSettingsFile(), json);
            LOG.infof("Saved settings to %s", getSettingsFile());
        } catch (IOException e) {
            LOG.errorf(e, "Failed to save settings to %s", getSettingsFile());
        }
    }

    // ========== Generic get/set ==========

    public String get(String key) {
        return settings.get(key);
    }

    public String get(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public synchronized void set(String key, String value) {
        settings.put(key, value);
        save();
    }

    // ========== LSP trace ==========

    public ServerTrace getLspTraceLevel(String serverId) {
        return lspTraceLevels.computeIfAbsent(serverId,
                id -> ServerTrace.fromValue(settings.get("lsp." + id + ".trace")));
    }

    public void setLspTraceLevel(String serverId, ServerTrace level) {
        lspTraceLevels.put(serverId, level);
        set("lsp." + serverId + ".trace", level.toString());
    }

    // ========== MCP trace ==========

    public ServerTrace getMcpTraceLevel() {
        ServerTrace cached = mcpTraceLevel;
        if (cached != null) {
            return cached;
        }
        cached = ServerTrace.fromValue(settings.get("mcp.trace"));
        mcpTraceLevel = cached;
        return cached;
    }

    public void setMcpTraceLevel(ServerTrace level) {
        mcpTraceLevel = level;
        set("mcp.trace", level.toString());
    }

    // ========== DAP trace ==========

    public ServerTrace getDapTraceLevel(String serverId) {
        return dapTraceLevels.computeIfAbsent(serverId,
                id -> ServerTrace.fromValue(settings.get("dap." + id + ".trace")));
    }

    public void setDapTraceLevel(String serverId, ServerTrace level) {
        dapTraceLevels.put(serverId, level);
        set("dap." + serverId + ".trace", level.toString());
    }
}
