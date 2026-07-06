package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.server.ServerCreateParams;
import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Parameters for creating a DAP server instance.
 * Extends base parameters with DAP-specific sessionId.
 */
public class DapServerCreateParams extends ServerCreateParams<DapServerConfig> {

    private final String sessionId;

    public DapServerCreateParams(String sessionId, DapServerConfig config, Workspace workspace) {
        super(config, workspace);
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
