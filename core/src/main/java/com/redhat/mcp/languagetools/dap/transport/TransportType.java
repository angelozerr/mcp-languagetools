package com.redhat.mcp.languagetools.dap.transport;

/**
 * DAP transport type.
 */
public enum TransportType {
    /**
     * Standard input/output (default).
     * The DAP client launches the server process and communicates via stdin/stdout.
     */
    STDIO,

    /**
     * TCP socket connection.
     * The DAP client connects to a server listening on a TCP port.
     */
    SOCKET;

    /**
     * Parse transport type from string.
     *
     * @param value the string value (case-insensitive)
     * @return the transport type, or STDIO if invalid
     */
    public static TransportType get(String value) {
        if (value == null) {
            return STDIO;
        }
        try {
            return TransportType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return STDIO;
        }
    }
}
