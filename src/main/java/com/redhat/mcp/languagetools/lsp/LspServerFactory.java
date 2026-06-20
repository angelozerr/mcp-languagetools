package com.redhat.mcp.languagetools.lsp;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;

import java.net.URI;
import java.nio.file.Path;

/**
 * SPI interface for creating custom LSP server implementations.
 * Extensions implement this interface and register via ServiceLoader.
 */
public interface LspServerFactory {

    /**
     * Get the server ID that this factory handles (e.g., "jdtls", "microprofile").
     */
    String getServerId();

    /**
     * Create a custom LSP server instance.
     */
    LspServer createServer(LspServerConfig config, URI workspaceRoot,
                          Path workspaceDataDir, Path serverHome,
                          LspTraceCollector traceCollector);
}
