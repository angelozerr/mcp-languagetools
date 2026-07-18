package com.ibm.mcp.languagetools.admin.ws;

import com.ibm.mcp.languagetools.trace.TraceCollector;

public class LspTraceWsMessage extends TraceWsMessage {

    private final String workspaceUri;
    private final String serverId;

    public LspTraceWsMessage(String workspaceUri, String serverId, String content, TraceCollector.MessageType messageType) {
        super(WsMessageType.LSP_TRACE, content, messageType);
        this.workspaceUri = workspaceUri;
        this.serverId = serverId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getServerId() {
        return serverId;
    }
}
