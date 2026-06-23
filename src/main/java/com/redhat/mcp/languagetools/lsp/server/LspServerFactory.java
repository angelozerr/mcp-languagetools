package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

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
     *
     * @param config This server's configuration
     * @param workspaceRoot Workspace root URI
     * @param workspaceDataDir Workspace data directory
     * @param serverHome Server installation directory
     * @param traceCollector Trace collector for LSP messages
     * @param allServerConfigs All server configurations (for reading contributes)
     */
    LspServer createServer(LspServerConfig config, URI workspaceRoot,
                           Path workspaceDataDir, Path serverHome,
                           LspTraceCollector traceCollector,
                           List<LspServerConfig> allServerConfigs);
}
