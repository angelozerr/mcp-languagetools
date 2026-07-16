package com.redhat.mcp.languagetools.server;

import com.redhat.mcp.languagetools.installer.InstallResult;

public class ServerConfigInstalledEvent {

    private final ServerConfigBase config;
    private final InstallResult result;

    public ServerConfigInstalledEvent(ServerConfigBase config, InstallResult result) {
        this.config = config;
        this.result = result;
    }

    public ServerConfigBase getConfig() {
        return config;
    }

    public InstallResult getResult() {
        return result;
    }
}
