package com.redhat.mcp.languagetools.lsp;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
     * Priority:
     * 1. Custom factory registered via SPI (e.g., JdtLsServer) - for servers with special needs
     * 2. ClasspathExtensibleLspServer - default for all other servers
     *
     * ClasspathExtensibleLspServer automatically detects and applies jarExtensions contributions.
     * If no extensions exist, it behaves exactly like the base LspServer.
     *
     * @param allServerConfigs All server configurations (for reading contributes)
     */
    public static LspServer createServer(LspServerConfig config, URI workspaceRoot,
                                         Path workspaceDataDir, Path serverHome,
                                         LspTraceCollector traceCollector,
                                         List<LspServerConfig> allServerConfigs) {

        // Check for custom factory (highest priority)
        LspServerFactory factory = factories.get(config.getId());
        if (factory != null) {
            LOG.infof("Creating custom LSP server for %s (workspace: %s)", config.getId(), workspaceRoot);
            return factory.createServer(config, workspaceRoot, workspaceDataDir, serverHome, traceCollector, allServerConfigs);
        }

        // Default: use classpath-extensible server (supports jarExtensions contributions)
        LOG.debugf("Creating classpath-extensible LSP server for %s", config.getId());
        return new ClasspathExtensibleLspServer(config, workspaceRoot, workspaceDataDir, serverHome, traceCollector, allServerConfigs);
    }
}
