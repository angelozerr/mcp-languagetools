package com.ibm.mcp.languagetools.mcp.trace;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RawMessage;

/**
 * No-op MCP trace collector that discards all traces.
 */
public class NoOpMcpTraceCollector extends McpTraceCollector {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void addTrace(String workspaceUri, String contextId, String content, MessageType type) {
    }

    @Override
    public void addTrace(McpTraceDirection direction, RawMessage message, McpConnection connection) {
    }
}
