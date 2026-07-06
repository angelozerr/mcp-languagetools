package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.server.ServerFactoryRegistryBase;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

/**
 * Registry for LSP server factories.
 * Uses SPI (ServiceLoader) to discover custom server factory implementations.
 * Factories are selected based on canHandle() method and results are cached.
 */
public class LspServerFactoryRegistry extends ServerFactoryRegistryBase<LspServerConfig, LspServer, LspServerFactory> {

    private static final Logger LOG = Logger.getLogger(LspServerFactoryRegistry.class);
    private static final LspServerFactoryRegistry INSTANCE = new LspServerFactoryRegistry();

    private final LspServerFactory defaultFactory = new DefaultLspServerFactory();

    private LspServerFactoryRegistry() {
        super(LspServerFactory.class, LOG);
    }

    public static LspServerFactoryRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Create an LSP server instance based on the config.
     */
    public LspServer createServer(LspServerConfig config, Workspace workspace) {
        return createServer(config, workspace, config.getServerId());
    }

    @Override
    protected String getFactoryServerId(LspServerFactory factory) {
        return factory.getServerId();
    }

    @Override
    protected boolean canHandleConfig(LspServerFactory factory, LspServerConfig config, Workspace workspace) {
        return factory.canHandle(config, workspace);
    }

    @Override
    protected LspServer createServerFromFactory(LspServerFactory factory, LspServerConfig config, Workspace workspace) {
        return factory.createServer(config, workspace);
    }

    @Override
    protected LspServerFactory getDefaultFactory() {
        return defaultFactory;
    }
}
