package com.redhat.mcp.languagetools.server;

public class ServerConfigAddedEvent {

    private final ServerConfigBase config;

    public ServerConfigAddedEvent(ServerConfigBase config) {
        this.config = config;
    }

    public ServerConfigBase getConfig() {
        return config;
    }
}
