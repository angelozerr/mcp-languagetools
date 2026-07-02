package com.redhat.mcp.languagetools.trace;

/**
 * Interface for collecting trace messages during server operations.
 * Allows installer and other components to send trace messages without
 * coupling to specific LSP/DAP trace implementations.
 */
public interface TraceCollector {

    /**
     * Message type - determines how the trace is displayed.
     */
    enum MessageType {
        TRACE,   // Normal trace line (default) - creates new line
        UPDATE,  // Update previous line instead of creating new one
        ERROR,   // Error message
        INFO     // Info message
    }

    enum MessageDirection {
        CLIENT_TO_SERVER,
        SERVER_TO_CLIENT
    }

    /**
     * Send a trace message with specific level and type.
     *
     * @param message Message to trace
     * @param type    Message type (TRACE, UPDATE, ERROR, INFO)
     */
    void trace(String message, MessageType type);

    /**
     * Send a trace message with default type (TRACE).
     *
     * @param message Message to trace
     */
    default void trace(String message) {
        trace(message, MessageType.TRACE);
    }

    /**
     * Send an info trace message.
     */
    default void info(String message) {
        trace(message, MessageType.INFO);
    }

    /**
     * Send an error trace message.
     */
    default void error(String message) {
        trace(message, MessageType.ERROR);
    }

    /**
     * Send an update message (replaces previous line instead of creating new one).
     * Useful for progress indicators.
     */
    default void update(String message) {
        trace(message, MessageType.UPDATE);
    }

    /**
     * Add a trace message with workspace context.
     */
    void addTrace(String workspaceUri, String serverId, MessageDirection direction, String message);
}
