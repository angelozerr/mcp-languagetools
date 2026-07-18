package com.ibm.mcp.languagetools.dap.server;

import com.ibm.mcp.languagetools.dap.session.DapSession;
import com.ibm.mcp.languagetools.server.ServerCreateParams;
import com.ibm.mcp.languagetools.workspace.Workspace;

/**
 * Parameters for creating a DAP server instance.
 * Extends base parameters with DAP-specific session reference.
 */
public class DapServerCreateParams extends ServerCreateParams<DapServerConfig> {

    private final DapSession session;

    public DapServerCreateParams(DapSession session, DapServerConfig config, Workspace workspace) {
        super(config, workspace);
        this.session = session;
    }

    public DapSession getSession() {
        return session;
    }
}
