package com.ibm.mcp.languagetools.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.mcp.languagetools.PathManager;
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
public class ApplicationConfiguration extends AbstractConfiguration {

    private static final Logger LOG = Logger.getLogger(ApplicationConfiguration.class);
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    @Inject
    PathManager pathManager;

    private final Map<String, ServerTrace> lspTraceLevels = new ConcurrentHashMap<>();
    private final Map<String, ServerTrace> dapTraceLevels = new ConcurrentHashMap<>();
    private volatile ServerTrace mcpTraceLevel;

    @PostConstruct
    void init() {
        load();
    }

    @Override
    protected Path getSettingsFile() {
        return pathManager.getSettingsFile();
    }

    // ========== Write support ==========

    public synchronized void set(String key, String value) {
        getSettings().put(key, value);
        save();
    }

    private synchronized void save() {
        try {
            Files.createDirectories(getSettingsFile().getParent());
            String json = PRETTY_GSON.toJson(getSettings());
            Files.writeString(getSettingsFile(), json);
            LOG.infof("Saved settings to %s", getSettingsFile());
        } catch (IOException e) {
            LOG.errorf(e, "Failed to save settings to %s", getSettingsFile());
        }
    }

    // ========== Trace entries ==========

    public Map<String, String> getTraceLevelEntries() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : getSettings().entrySet()) {
            if (entry.getKey().endsWith(".trace")) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    // ========== LSP trace ==========

    public ServerTrace getLspTraceLevel(String serverId) {
        return lspTraceLevels.computeIfAbsent(serverId,
                id -> ServerTrace.fromValue(getString("lsp." + id + ".trace")));
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
        cached = ServerTrace.fromValue(getString("mcp.trace"));
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
                id -> ServerTrace.fromValue(getString("dap." + id + ".trace")));
    }

    public void setDapTraceLevel(String serverId, ServerTrace level) {
        dapTraceLevels.put(serverId, level);
        set("dap." + serverId + ".trace", level.toString());
    }
}
