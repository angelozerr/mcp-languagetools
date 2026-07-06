package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.server.ServerFactory;

/**
 * Factory for creating DAP server instances.
 * Implementations can provide custom DAP server subclasses for specific debuggers.
 *
 * <p>This follows the same SPI pattern as LspServerFactory.</p>
 *
 * <p>Example: JavaDebugServerFactory creates JavaDebugServer instances that handle
 * Java-specific resolution (classpath, java executable, etc.)</p>
 */
public interface DapServerFactory extends ServerFactory<DapServerConfig, DapServer, DapServerCreateParams> {
}
