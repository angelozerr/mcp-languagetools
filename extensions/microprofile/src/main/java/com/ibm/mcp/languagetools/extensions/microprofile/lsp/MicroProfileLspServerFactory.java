package com.ibm.mcp.languagetools.extensions.microprofile.lsp;

import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerCreateParams;
import com.ibm.mcp.languagetools.lsp.server.LspServerFactory;

public class MicroProfileLspServerFactory implements LspServerFactory {

    @Override
    public String getServerId() {
        return "microprofile";
    }

    @Override
    public LspServer createServer(LspServerCreateParams params) {
        return new MicroProfileLspServer(params.getConfig(), params.getWorkspace());
    }
}
