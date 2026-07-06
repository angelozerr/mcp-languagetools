package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.server.ServerFactoryRegistryBase;
import com.redhat.mcp.languagetools.workspace.Workspace;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

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
     * Convenience method that wraps sessionId, config and workspace in params.
     *
     * @param sessionId The session ID (for embedded servers like java-debug)
     * @param config The DAP server configuration
     * @param workspace The workspace
     * @return The created DAP server (never null - falls back to default)
     */
    public DapServer createServer(String sessionId, DapServerConfig config, Workspace workspace) {
        return createServer(new DapServerCreateParams(sessionId, config, workspace));
    }

    @Override
    protected DapServerFactory getDefaultFactory() {
        return defaultFactory;
    }

}
