package com.ibm.mcp.languagetools.admin.ws;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public abstract class WsMessage {

    private final WsMessageType type;

    protected WsMessage(WsMessageType type) {
        this.type = type;
    }

    public WsMessageType getType() {
        return type;
    }
}
