package com.redhat.mcp.languagetools.jdtls.dap;

import com.redhat.mcp.languagetools.dap.server.DapServer;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.dap.server.DapServerFactory;
import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Factory for creating JavaDebugServer instances.
 * Discovered via Java SPI (ServiceLoader).
 */
public class JavaDebugServerFactory implements DapServerFactory {

    private static final String JAVA_DEBUG_SERVER_ID = "java-debug";

    @Override
    public boolean canHandle(String serverId) {
        return JAVA_DEBUG_SERVER_ID.equals(serverId);
    }

    @Override
    public DapServer createServer(String sessionId, DapServerConfig config, Workspace workspace) {
        return new JavaDebugServer(sessionId, config, workspace);
    }
}
