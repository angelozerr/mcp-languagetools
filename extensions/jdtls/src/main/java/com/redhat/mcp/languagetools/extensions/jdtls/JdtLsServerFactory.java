package com.redhat.mcp.languagetools.extensions.jdtls;

import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerConfig;
import com.redhat.mcp.languagetools.lsp.server.LspServerCreateParams;
import com.redhat.mcp.languagetools.lsp.server.LspServerFactory;
import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Factory for creating JDT.LS custom server instances.
 */
public class JdtLsServerFactory implements LspServerFactory {

    @Override
    public String getServerId() {
        return "jdtls";
    }

    @Override
    public LspServer createServer(LspServerCreateParams params) {
        return new JdtLsServer(params.getConfig(), params.getWorkspace());
    }

}
