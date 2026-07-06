package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.server.ServerFactory;
import com.redhat.mcp.languagetools.workspace.Workspace;

import java.util.Objects;

/**
 * SPI interface for creating custom LSP server implementations.
 * Extensions implement this interface and register via ServiceLoader.
 */
public interface LspServerFactory extends ServerFactory<LspServerConfig, LspServer> {

    /**
     * Create a custom LSP server instance.
     *
     * @param config This server's configuration
     * @param workspace The workspace this server belongs to
     */
    LspServer createServer(LspServerConfig config, Workspace workspace);
}
