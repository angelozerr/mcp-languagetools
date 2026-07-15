package com.redhat.mcp.languagetools.admin.ws;

import com.redhat.mcp.languagetools.trace.TraceCollector;

public class DapTraceWsMessage extends TraceWsMessage {

    private final String workspaceUri;
    private final String sessionId;

    public DapTraceWsMessage(String workspaceUri, String sessionId, String content, TraceCollector.MessageType messageType) {
        super(WsMessageType.DAP_TRACE, content, messageType);
        this.workspaceUri = workspaceUri;
        this.sessionId = sessionId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getSessionId() {
        return sessionId;
    }
}
