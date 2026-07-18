package com.ibm.mcp.languagetools.lsp.server;

import com.ibm.mcp.languagetools.server.ServerFactory;

/**
 * SPI interface for creating custom LSP server implementations.
 * Extensions implement this interface and register via ServiceLoader.
 */
public interface LspServerFactory extends ServerFactory<LspServerConfig, LspServer, LspServerCreateParams> {
}
