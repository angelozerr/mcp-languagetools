package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Factory for creating DAP server instances.
 * Implementations can provide custom DAP server subclasses for specific debuggers.
 *
 * <p>This follows the same SPI pattern as LspServerFactory.</p>
 *
 * <p>Example: JavaDebugServerFactory creates JavaDebugServer instances that handle
 * Java-specific resolution (classpath, java executable, etc.)</p>
 */
public interface DapServerFactory {

    /**
     * Check if this factory can create a server for the given server ID.
     *
     * @param serverId The DAP server ID (e.g., "java-debug", "vscode-js-debug")
     * @return true if this factory handles this server ID
     */
    boolean canHandle(String serverId);

    /**
     * Create a DAP server instance.
     *
     * @param sessionId The session ID (for embedded servers like java-debug)
     * @param config The DAP server configuration
     * @param workspace The workspace
     * @return A new DAP server instance
     */
    DapServer createServer(String sessionId, DapServerConfig config, Workspace workspace);
}
