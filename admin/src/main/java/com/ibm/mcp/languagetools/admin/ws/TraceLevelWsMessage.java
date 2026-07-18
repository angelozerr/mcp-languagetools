package com.ibm.mcp.languagetools.admin.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

public class TraceLevelWsMessage extends WsMessage {

    private final String serverType;
    private final String serverId;
    private final String traceLevel;

    public TraceLevelWsMessage(String serverType, String serverId, String traceLevel) {
        super(WsMessageType.TRACE_LEVEL_UPDATE);
        this.serverType = serverType;
        this.serverId = serverId;
        this.traceLevel = traceLevel;
    }

    public String getServerType() {
        return serverType;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getServerId() {
        return serverId;
    }

    public String getTraceLevel() {
        return traceLevel;
    }
}
