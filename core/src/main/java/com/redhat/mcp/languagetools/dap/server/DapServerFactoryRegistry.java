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
public class DapServerFactoryRegistry extends ServerFactoryRegistryBase<DapServerConfig, DapServer, DapServerFactory> {

    private static final Logger LOG = Logger.getLogger(DapServerFactoryRegistry.class);
    private static final DapServerFactoryRegistry INSTANCE = new DapServerFactoryRegistry();

    private final DapServerFactory defaultFactory = new DefaultDapServerFactory();

    private DapServerFactoryRegistry() {
        super(DapServerFactory.class, LOG);
    }

    public static DapServerFactoryRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Find a factory that can handle the given configuration and create a server.
     *
     * @param sessionId The session ID (for embedded servers like java-debug)
     * @param config The DAP server configuration
     * @param workspace The workspace
     * @return The created DAP server (never null - falls back to default)
     */
    public DapServer createServer(String sessionId, DapServerConfig config, Workspace workspace) {
        return createServer(config, workspace, config.getServerId(), sessionId);
    }

    /**
     * Create a server with session ID support.
     */
    private DapServer createServer(DapServerConfig config, Workspace workspace, String serverId, String sessionId) {
        // Use base class logic but adapt for DAP's sessionId parameter
        DapServerFactory factory = findFactory(config, workspace, serverId);
        return factory.createServer(sessionId, config, workspace);
    }

    /**
     * Find a factory that can handle the given configuration.
     */
    private DapServerFactory findFactory(DapServerConfig config, Workspace workspace, String serverId) {
        // Check cache first
        DapServerFactory cachedFactory = factoryCache.get(serverId);
        if (cachedFactory != null) {
            log.debugf("Using cached factory for %s: %s", serverId, cachedFactory.getClass().getSimpleName());
            return cachedFactory;
        }

        // Iterate through SPI factories
        for (DapServerFactory factory : spiFactories) {
            if (factory.canHandle(config, workspace)) {
                log.infof("Factory %s can handle %s", factory.getClass().getSimpleName(), serverId);
                factoryCache.put(serverId, factory);
                return factory;
            }
        }

        // No additional factories for DAP (no equivalent of ClasspathExtensible)

        // Ultimate fallback
        log.debugf("No specific factory found for %s, using default factory", serverId);
        factoryCache.put(serverId, defaultFactory);
        return defaultFactory;
    }

    @Override
    protected String getFactoryServerId(DapServerFactory factory) {
        return factory.getServerId();
    }

    @Override
    protected boolean canHandleConfig(DapServerFactory factory, DapServerConfig config, Workspace workspace) {
        return factory.canHandle(config, workspace);
    }

    @Override
    protected DapServer createServerFromFactory(DapServerFactory factory, DapServerConfig config, Workspace workspace) {
        throw new UnsupportedOperationException("Use createServer(sessionId, config, workspace) instead");
    }

    @Override
    protected DapServerFactory getDefaultFactory() {
        return defaultFactory;
    }

}
