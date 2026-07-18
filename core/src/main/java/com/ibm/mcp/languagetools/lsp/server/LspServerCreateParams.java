package com.ibm.mcp.languagetools.lsp.server;

import com.ibm.mcp.languagetools.server.ServerCreateParams;
import com.ibm.mcp.languagetools.workspace.Workspace;

/**
 * Parameters for creating an LSP server instance.
 */
public class LspServerCreateParams extends ServerCreateParams<LspServerConfig> {

    public LspServerCreateParams(LspServerConfig config, Workspace workspace) {
        super(config, workspace);
    }
}
