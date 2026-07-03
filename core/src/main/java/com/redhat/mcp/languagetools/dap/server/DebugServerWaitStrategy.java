package com.redhat.mcp.languagetools.dap.server;

/**
 * Debug server wait strategy.
 */
public enum DebugServerWaitStrategy {

    /** Waits for a fixed timeout before assuming the server is ready. */
    TIMEOUT,

    /** Waits for a specific trace/log message indicating server readiness. */
    TRACE;

    /**
     * Retrieves the corresponding DebugServerWaitStrategy from a string value.
     *
     * @param value the string representation of the strategy (case-insensitive).
     * @return the matching DebugServerWaitStrategy, or TIMEOUT if the input is invalid.
     */
    public static DebugServerWaitStrategy get(String value) {
        try {
            return DebugServerWaitStrategy.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return DebugServerWaitStrategy.TIMEOUT;
        }
    }
}
