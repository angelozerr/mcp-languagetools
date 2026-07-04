package com.redhat.mcp.languagetools.dap.server;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Registry for DAP server factories.
 * Discovers and manages all available DapServerFactory implementations via Java SPI (ServiceLoader).
 */
@ApplicationScoped
public class DapServerFactoryRegistry {

    private static final Logger LOG = Logger.getLogger(DapServerFactoryRegistry.class);

    private final List<DapServerFactory> factories;

    public DapServerFactoryRegistry() {
        // Load factories via Java ServiceLoader (META-INF/services)
        this.factories = new ArrayList<>();
        ServiceLoader<DapServerFactory> loader = ServiceLoader.load(DapServerFactory.class);
        loader.forEach(factories::add);

        LOG.infof("Loaded %d DAP server factories via SPI", factories.size());
        factories.forEach(f -> LOG.infof("  - %s", f.getClass().getName()));
    }

    /**
     * Find a factory that can handle the given server ID.
     *
     * @param serverId The DAP server ID
     * @return The factory, or null if none found
     */
    public DapServerFactory findFactory(String serverId) {
        for (DapServerFactory factory : factories) {
            if (factory.canHandle(serverId)) {
                LOG.infof("Found factory %s for DAP server: %s", factory.getClass().getName(), serverId);
                return factory;
            }
        }
        LOG.infof("No factory found for DAP server: %s, using default", serverId);
        return null;
    }

    /**
     * Get all registered factories.
     *
     * @return List of all factories
     */
    public List<DapServerFactory> getAllFactories() {
        return new ArrayList<>(factories);
    }
}
