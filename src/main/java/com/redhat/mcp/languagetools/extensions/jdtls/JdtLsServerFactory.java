package com.redhat.mcp.languagetools.extensions.jdtls;

import com.redhat.mcp.languagetools.lsp.LspServer;
import com.redhat.mcp.languagetools.lsp.LspServerConfig;
import com.redhat.mcp.languagetools.lsp.LspServerFactory;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Factory for creating JDT.LS custom server instances.
 */
public class JdtLsServerFactory implements LspServerFactory {

    @Override
    public String getServerId() {
        return "jdtls";
    }

    @Override
    public LspServer createServer(LspServerConfig config, URI workspaceRoot,
                                  Path workspaceDataDir, Path serverHome,
                                  LspTraceCollector traceCollector,
                                  List<LspServerConfig> allServerConfigs) {
        return new JdtLsServer(config, workspaceRoot, workspaceDataDir, serverHome, traceCollector, allServerConfigs);
    }
}
