package com.ibm.mcp.languagetools.server;

import com.ibm.mcp.languagetools.workspace.Workspace;

/**
 * Parameters for creating a server instance.
 * Base class for LSP and DAP server creation parameters.
 *
 * @param <C> Config type (LspServerConfig or DapServerConfig)
 */
public class ServerCreateParams<C extends ServerConfigBase> {

    private final C config;
    private final Workspace workspace;

    public ServerCreateParams(C config, Workspace workspace) {
        this.config = config;
        this.workspace = workspace;
    }

    public C getConfig() {
        return config;
    }

    public Workspace getWorkspace() {
        return workspace;
    }
}
