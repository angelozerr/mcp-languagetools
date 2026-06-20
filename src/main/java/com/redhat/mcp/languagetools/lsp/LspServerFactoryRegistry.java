package com.redhat.mcp.languagetools.lsp;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Registry for LSP server factories.
 * Uses SPI (ServiceLoader) to discover custom server factory implementations.
 */
public class LspServerFactoryRegistry {

    private static final Logger LOG = Logger.getLogger(LspServerFactoryRegistry.class);
    private static final Map<String, LspServerFactory> factories = new HashMap<>();

    static {
        // Load all LspServerFactory implementations via SPI
        ServiceLoader<LspServerFactory> loader = ServiceLoader.load(LspServerFactory.class);
        for (LspServerFactory factory : loader) {
            factories.put(factory.getServerId(), factory);
            LOG.infof("Registered custom LSP server factory for: %s", factory.getServerId());
        }
    }

    /**
     * Create an LSP server instance based on the config.
     * Returns a custom implementation if a factory is registered,
     * or the default LspServer otherwise.
     */
    public static LspServer createServer(LspServerConfig config, URI workspaceRoot,
                                         Path workspaceDataDir, Path serverHome,
                                         LspTraceCollector traceCollector) {

        LspServerFactory factory = factories.get(config.getId());
        if (factory != null) {
            LOG.infof("Creating custom LSP server for %s (workspace: %s)", config.getId(), workspaceRoot);
            return factory.createServer(config, workspaceRoot, workspaceDataDir, serverHome, traceCollector);
        }

        LOG.debugf("Creating default LSP server for %s", config.getId());
        return new LspServer(config, workspaceRoot, workspaceDataDir, serverHome, traceCollector);
    }
}
