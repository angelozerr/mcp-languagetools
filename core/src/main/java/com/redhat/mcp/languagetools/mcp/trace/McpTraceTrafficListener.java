package com.redhat.mcp.languagetools.mcp.trace;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpTrafficListener;
import io.quarkiverse.mcp.server.RawMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class McpTraceTrafficListener implements McpTrafficListener {

    @Inject
    McpTraceCollector traceCollector;

    @Override
    public void onMessageReceived(RawMessage message, McpConnection connection) {
        traceCollector.addTrace(McpTraceDirection.RECEIVED, message, connection);
    }

    @Override
    public void onMessageSent(RawMessage message, McpConnection connection) {
        traceCollector.addTrace(McpTraceDirection.SENT, message, connection);
    }
}
