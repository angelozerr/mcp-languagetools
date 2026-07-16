package com.redhat.mcp.languagetools.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class OSUtils {

    private static final String WINDOWS = "windows";
    private static final String MAC = "mac";
    private static final String LINUX = "linux";
    private static final String DEFAULT = "default";

    public static final String OS_KEY;

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            OS_KEY = WINDOWS;
        } else if (os.contains("mac")) {
            OS_KEY = MAC;
        } else {
            OS_KEY = LINUX;
        }
    }

    public static boolean isWindows() {
        return WINDOWS.equals(OS_KEY);
    }

    public static boolean isMac() {
        return MAC.equals(OS_KEY);
    }

    public static boolean isLinux() {
        return LINUX.equals(OS_KEY);
    }

    /**
     * Resolve an OS-specific string from a JSON property.
     * The property value can be either:
     * <ul>
     *   <li>A simple string (returned as-is)</li>
     *   <li>An object with OS keys: {@code {"windows": "...", "mac": "...", "linux": "...", "default": "..."}}</li>
     * </ul>
     *
     * @param json     the JSON object containing the property
     * @param property the property name to resolve
     * @return the resolved string, or null if not found
     */
    public static String getStringFromOs(JsonObject json, String property) {
        if (!json.has(property)) {
            return null;
        }
        JsonElement element = json.get(property);
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            return getStringFromOs(element.getAsJsonObject());
        }
        return null;
    }

    /**
     * Resolve an OS-specific string from a JSON object with OS keys.
     *
     * @param osMap the JSON object with OS keys (windows, mac, linux, default)
     * @return the resolved string for the current OS, or null
     */
    public static String getStringFromOs(JsonObject osMap) {
        if (osMap.has(OS_KEY)) {
            JsonElement value = osMap.get(OS_KEY);
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        if (osMap.has(DEFAULT)) {
            JsonElement value = osMap.get(DEFAULT);
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    /**
     * Resolve an OS-specific string from a Map (used by server configs
     * where JSON is already parsed to Map).
     *
     * @param map the map with OS keys
     * @return the resolved string for the current OS, or null
     */
    public static String getStringFromOs(java.util.Map<String, String> map) {
        if (map == null) {
            return null;
        }
        String value = map.get(OS_KEY);
        if (value != null) {
            return value;
        }
        return map.get(DEFAULT);
    }

    private OSUtils() {
    }
}
