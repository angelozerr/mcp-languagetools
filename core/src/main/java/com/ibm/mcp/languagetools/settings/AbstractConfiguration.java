package com.ibm.mcp.languagetools.settings;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for JSON-based configuration files (application settings, workspace settings).
 * Loads a settings.json file into a Map and provides typed accessors with dot-notation support.
 */
public abstract class AbstractConfiguration {

    private static final Logger LOG = Logger.getLogger(AbstractConfiguration.class);
    private static final Gson GSON = new Gson();

    private Map<String, Object> settings = new HashMap<>();

    /**
     * Return the path to the JSON settings file to load.
     */
    protected abstract Path getSettingsFile();

    /**
     * Load settings from the JSON file returned by {@link #getSettingsFile()}.
     */
    protected void load() {
        Path settingsFile = getSettingsFile();
        if (settingsFile == null || !Files.exists(settingsFile)) {
            LOG.debugf("No settings file found at: %s", settingsFile);
            settings = new HashMap<>();
            return;
        }

        try {
            String json = Files.readString(settingsFile);
            TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {
            };
            Map<String, Object> loaded = GSON.fromJson(json, typeToken.getType());
            settings = loaded != null ? loaded : new HashMap<>();
            LOG.infof("Loaded settings from %s", settingsFile);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to load settings from %s", settingsFile);
            settings = new HashMap<>();
        }
    }

    /**
     * Return the internal settings map.
     */
    protected Map<String, Object> getSettings() {
        return settings;
    }

    // ========== Accessors ==========

    public Object get(String key) {
        return get(key, null);
    }

    public Object get(String key, Object defaultValue) {
        Object value = getNestedValue(settings, key);
        return value != null ? value : defaultValue;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        Object value = get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return value != null ? String.valueOf(value) : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public Map<String, Object> getAll() {
        return new HashMap<>(settings);
    }

    // ========== Nested key resolution ==========

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        if (map.containsKey(key)) {
            return map.get(key);
        }

        String[] parts = key.split("\\.", 2);
        if (parts.length == 1) {
            return map.get(key);
        }

        Object current = map.get(parts[0]);
        if (current instanceof Map) {
            return getNestedValue((Map<String, Object>) current, parts[1]);
        }

        return null;
    }
}
