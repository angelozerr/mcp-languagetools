package com.redhat.mcp.languagetools.admin.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.redhat.mcp.languagetools.trace.TraceCollector;

public abstract class TraceWsMessage extends WsMessage {

    private final String content;
    private final TraceCollector.MessageType messageType;

    protected TraceWsMessage(WsMessageType type, String content, TraceCollector.MessageType messageType) {
        super(type);
        this.content = content;
        this.messageType = messageType == TraceCollector.MessageType.TRACE ? null : messageType;
    }

    public String getContent() {
        return content;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TraceCollector.MessageType getMessageType() {
        return messageType;
    }
}
