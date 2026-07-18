package com.ibm.mcp.languagetools.extensions.jdtls.lsp;

import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerCreateParams;
import com.ibm.mcp.languagetools.lsp.server.LspServerFactory;

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
