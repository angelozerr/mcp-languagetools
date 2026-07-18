package com.ibm.mcp.languagetools.dap.server;

import com.ibm.mcp.languagetools.dap.session.DapSession;
import com.ibm.mcp.languagetools.server.ServerFactoryRegistryBase;
import com.ibm.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

/**
 * Registry for DAP server factories.
 * Discovers and manages all available DapServerFactory implementations via Java SPI (ServiceLoader).
 * Factories are selected based on canHandle() method and results are cached.
 */
public class DapServerFactoryRegistry extends ServerFactoryRegistryBase<DapServerConfig, DapServer, DapServerCreateParams, DapServerFactory> {

    private static final Logger LOG = Logger.getLogger(DapServerFactoryRegistry.class);
    private static final DapServerFactoryRegistry INSTANCE = new DapServerFactoryRegistry();

    private final DapServerFactory defaultFactory = new DefaultDapServerFactory();

    private DapServerFactoryRegistry() {
        super(DapServerFactory.class);
    }

    public static DapServerFactoryRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Create a DAP server instance based on the config.
     * Convenience method that wraps session, config and workspace in params.
     *
     * @param session The DAP session
     * @param config The DAP server configuration
     * @param workspace The workspace
     * @return The created DAP server (never null - falls back to default)
     */
    public DapServer createServer(DapSession session, DapServerConfig config, Workspace workspace) {
        return createServer(new DapServerCreateParams(session, config, workspace));
    }

    @Override
    protected DapServerFactory getDefaultFactory() {
        return defaultFactory;
    }

}
