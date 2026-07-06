package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.server.ServerFactory;

/**
 * SPI interface for creating custom LSP server implementations.
 * Extensions implement this interface and register via ServiceLoader.
 */
public interface LspServerFactory extends ServerFactory<LspServerConfig, LspServer, LspServerCreateParams> {
}
