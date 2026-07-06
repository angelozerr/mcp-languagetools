package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.server.ServerFactory;
import com.redhat.mcp.languagetools.workspace.Workspace;

import java.util.Objects;

/**
 * Factory for creating DAP server instances.
 * Implementations can provide custom DAP server subclasses for specific debuggers.
 *
 * <p>This follows the same SPI pattern as LspServerFactory.</p>
 *
 * <p>Example: JavaDebugServerFactory creates JavaDebugServer instances that handle
 * Java-specific resolution (classpath, java executable, etc.)</p>
 */
public interface DapServerFactory extends ServerFactory<DapServerConfig, DapServer> {

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
