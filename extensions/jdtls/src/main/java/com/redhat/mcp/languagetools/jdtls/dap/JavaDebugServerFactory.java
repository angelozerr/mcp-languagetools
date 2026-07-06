package com.redhat.mcp.languagetools.jdtls.dap;

import com.redhat.mcp.languagetools.dap.server.DapServer;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.dap.server.DapServerCreateParams;
import com.redhat.mcp.languagetools.dap.server.DapServerFactory;
import com.redhat.mcp.languagetools.workspace.Workspace;

/**
 * Factory for creating JavaDebugServer instances.
 * Discovered via Java SPI (ServiceLoader).
 */
public class JavaDebugServerFactory implements DapServerFactory {

    @Override
    public String getServerId() {
        return "java-debug";
    }

    @Override
    public DapServer createServer(DapServerCreateParams params) {
        return new JavaDebugServer(params.getSessionId(), params.getConfig(), params.getWorkspace());
    }

}
