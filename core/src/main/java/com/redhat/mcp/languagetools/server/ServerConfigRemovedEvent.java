package com.redhat.mcp.languagetools.server;

public class ServerConfigRemovedEvent {

    private final ServerConfigBase config;

    public ServerConfigRemovedEvent(ServerConfigBase config) {
        this.config = config;
    }

    public ServerConfigBase getConfig() {
        return config;
    }
}
