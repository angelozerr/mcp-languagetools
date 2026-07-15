package com.redhat.mcp.languagetools.admin.ws;

public class McpTraceWsMessage extends TraceWsMessage {

    private final String connectionId;

    public McpTraceWsMessage(String connectionId, String content) {
        super(WsMessageType.MCP_TRACE, content, null);
        this.connectionId = connectionId;
    }

    public String getConnectionId() {
        return connectionId;
    }
}
