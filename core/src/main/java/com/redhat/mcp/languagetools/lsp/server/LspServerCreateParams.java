package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.server.ServerCreateParams;
import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Parameters for creating an LSP server instance.
 */
public class LspServerCreateParams extends ServerCreateParams<LspServerConfig> {

    public LspServerCreateParams(LspServerConfig config, Workspace workspace) {
        super(config, workspace);
    }
}
