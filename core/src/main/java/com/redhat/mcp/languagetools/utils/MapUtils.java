package com.redhat.mcp.languagetools.utils;

import java.util.Map;

/**
 * Utility methods for extracting typed values from {@code Map<String, Object>},
 * as produced by JSON deserialization of MCP tool arguments.
 */
public final class MapUtils {

    private MapUtils() {
    }

    /**
     * Extract a string value from a map.
     *
     * @return the string value, or {@code null} if the key is absent or not a string
     */
    public static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Extract an integer value from a map.
     * Handles all {@link Number} subtypes (Integer, Long, Double, BigDecimal, etc.)
     * that JSON deserializers may produce.
     *
     * @return the integer value, or {@code null} if the key is absent or not a number
     */
    public static Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    /**
     * Extract a required integer value from a map.
     *
     * @return the integer value
     * @throws IllegalArgumentException if the key is absent or not a number
     */
    public static int requireInteger(Map<String, Object> map, String key) {
        Integer value = getInteger(map, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing or non-numeric value for key: " + key);
        }
        return value;
    }
}
