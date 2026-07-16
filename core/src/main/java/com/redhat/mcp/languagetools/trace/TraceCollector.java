package com.redhat.mcp.languagetools.trace;

/**
 * Collects trace messages from LSP/DAP servers.
 * <p>
 * Traces are identified by a workspace URI and a context ID:
 * <ul>
 *   <li>For LSP: contextId is the server ID (e.g. "jdtls")</li>
 *   <li>For DAP: contextId is "serverId#sessionId" for protocol traces, or just "serverId" for installation traces</li>
 * </ul>
 * <p>
 * Overloads without workspaceUri pass null, for traces not tied
 * to a specific workspace (e.g. installation traces).
 */
public interface TraceCollector {

    /**
     * Type of trace message, used to control display in the admin UI.
     */
    enum MessageType {
        /** Standard protocol trace (request/response/notification). */
        TRACE,
        /** Progress update during installation. */
        UPDATE,
        /** Error message. */
        ERROR,
        /** Informational message (e.g. installation step). */
        INFO
    }

    /**
     * Add a trace message with full context.
     *
     * @param workspaceUri the workspace URI, or null for global traces
     * @param contextId    the server ID (LSP), "serverId#sessionId" (DAP protocol), or serverId (DAP installation)
     * @param content      the trace content
     * @param type         the message type
     */
    void addTrace(String workspaceUri, String contextId, String content, MessageType type);

    /**
     * Add a trace message with default type {@link MessageType#TRACE}.
     *
     * @param workspaceUri the workspace URI, or null for global traces
     * @param contextId    the context identifier (see class javadoc)
     * @param content      the trace content
     */
    default void addTrace(String workspaceUri, String contextId, String content) {
        addTrace(workspaceUri, contextId, content, MessageType.TRACE);
    }

    /**
     * Add a trace message without workspace context (e.g. installation traces).
     *
     * @param contextId the context identifier (see class javadoc)
     * @param content   the trace content
     * @param type      the message type
     */
    default void addTrace(String contextId, String content, MessageType type) {
        addTrace(null, contextId, content, type);
    }

    /**
     * Add a trace message without workspace context, with default type {@link MessageType#TRACE}.
     *
     * @param contextId the context identifier (see class javadoc)
     * @param content   the trace content
     */
    default void addTrace(String contextId, String content) {
        addTrace(null, contextId, content, MessageType.TRACE);
    }
}
