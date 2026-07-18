package com.ibm.mcp.languagetools.dap.configurations;

/**
 * Base interface for pattern segments (static or dynamic).
 */
public interface Segment {
    /**
     * Check if this segment matches the input string.
     *
     * @param input the input string to match
     * @return the matched value, or null if no match
     */
    String matches(String input);

    /**
     * @return true if this is a dynamic segment (${...}), false for static text
     */
    boolean isDynamic();
}
