package com.redhat.mcp.languagetools.workspace;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.trace.TraceCollectorBase;

/**
 * Wrapper that adapts LspTraceCollector to TraceCollector interface.
 */
class LspTraceCollectorWrapper extends TraceCollectorBase {

    private final LspTraceCollector lspTraceCollector;
    private final String workspaceUri;
    private final String serverId;

    public LspTraceCollectorWrapper(LspTraceCollector lspTraceCollector,
                                    String workspaceUri,
                                    String serverId) {
        this.lspTraceCollector = lspTraceCollector;
        this.workspaceUri = workspaceUri;
        this.serverId = serverId;
    }

    @Override
    protected void addTrace(String message, MessageType type, String formattedMessage) {
        lspTraceCollector.addTrace(
                workspaceUri,
                serverId,
                formattedMessage,
                type
        );
    }

    @Override
    public void addTrace(String workspaceUri, String serverId, String message) {
        lspTraceCollector.addTrace(workspaceUri, serverId, message);
    }
}