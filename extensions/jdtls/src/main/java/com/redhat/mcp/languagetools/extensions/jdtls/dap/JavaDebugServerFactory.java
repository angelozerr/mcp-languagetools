package com.redhat.mcp.languagetools.extensions.jdtls.dap;

import com.redhat.mcp.languagetools.dap.server.DapServer;
import com.redhat.mcp.languagetools.dap.server.DapServerCreateParams;
import com.redhat.mcp.languagetools.dap.server.DapServerFactory;

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
        return new JavaDebugServer(params.getSession(), params.getConfig(), params.getWorkspace());
    }

}
