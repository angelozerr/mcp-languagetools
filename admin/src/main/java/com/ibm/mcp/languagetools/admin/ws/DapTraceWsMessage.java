package com.ibm.mcp.languagetools.admin.ws;

import com.ibm.mcp.languagetools.trace.TraceCollector;

public class DapTraceWsMessage extends TraceWsMessage {

    private final String workspaceUri;
    private final String serverId;
    private final String sessionId;

    public DapTraceWsMessage(String workspaceUri, String serverId, String sessionId, String content, TraceCollector.MessageType messageType) {
        super(WsMessageType.DAP_TRACE, content, messageType);
        this.workspaceUri = workspaceUri;
        this.serverId = serverId;
        this.sessionId = sessionId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getServerId() {
        return serverId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
