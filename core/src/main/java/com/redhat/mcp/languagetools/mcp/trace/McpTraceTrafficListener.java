package com.redhat.mcp.languagetools.mcp.trace;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.settings.Settings;
import com.redhat.mcp.languagetools.settings.ServerTrace;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpTrafficListener;
import io.quarkiverse.mcp.server.RawMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class McpTraceTrafficListener implements McpTrafficListener {

    @Inject
    Application application;

    @Inject
    Settings settings;

    @Override
    public void onMessageReceived(RawMessage message, McpConnection connection) {
        McpTraceCollector traceCollector = application.getMcpTraceCollector();
        if (traceCollector.isEnabled() && settings.getMcpTraceLevel() != ServerTrace.off) {
            traceCollector.addTrace(McpTraceDirection.RECEIVED, message, connection);
        }
    }

    @Override
    public void onMessageSent(RawMessage message, McpConnection connection) {
        McpTraceCollector traceCollector = application.getMcpTraceCollector();
        if (traceCollector.isEnabled() && settings.getMcpTraceLevel() != ServerTrace.off) {
            traceCollector.addTrace(McpTraceDirection.SENT, message, connection);
        }
    }
}
