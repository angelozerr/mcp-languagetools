package com.redhat.mcp.languagetools.server;

import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for server factory registries (LSP and DAP).
 * Handles common logic: SPI loading, sorting, caching, canHandle iteration.
 *
 * @param <C> Config type (LspServerConfig or DapServerConfig)
 * @param <S> Server type (LspServer or DapServer)
 * @param <F> Factory type (LspServerFactory or DapServerFactory)
 */
public abstract class ServerFactoryRegistryBase<C, S, F> {

    protected final Logger log;
    protected final List<F> spiFactories = new ArrayList<>();
    protected final Map<String, F> factoryCache = new ConcurrentHashMap<>();

    protected ServerFactoryRegistryBase(Class<F> factoryClass, Logger logger) {
        this.log = logger;

        // Load all factory implementations via SPI
        ServiceLoader<F> loader = ServiceLoader.load(factoryClass);
        for (F factory : loader) {
            spiFactories.add(factory);
            log.infof("Registered factory: %s", factory.getClass().getSimpleName());
        }

        // Sort factories: those with non-null getServerId() first (more specific)
        spiFactories.sort((f1, f2) -> {
            String id1 = getFactoryServerId(f1);
            String id2 = getFactoryServerId(f2);
            if (id1 != null && id2 == null) return -1;
            if (id1 == null && id2 != null) return 1;
            return 0;
        });
    }

    /**
     * Create a server instance based on the config.
     * Iterates through factories calling canHandle(), uses cache, and falls back to default.
     */
    protected S createServer(C config, Workspace workspace, String serverId) {
        // Check cache first
        F cachedFactory = factoryCache.get(serverId);
        if (cachedFactory != null) {
            log.debugf("Using cached factory for %s: %s", serverId, cachedFactory.getClass().getSimpleName());
            return createServerFromFactory(cachedFactory, config, workspace);
        }

        // Iterate through SPI factories to find one that can handle this config
        for (F factory : spiFactories) {
            if (canHandleConfig(factory, config, workspace)) {
                log.infof("Factory %s can handle %s", factory.getClass().getSimpleName(), serverId);
                factoryCache.put(serverId, factory);
                return createServerFromFactory(factory, config, workspace);
            }
        }

        // Ultimate fallback - default factory
        F defaultFactory = getDefaultFactory();
        log.debugf("No specific factory found for %s, using default factory", serverId);
        factoryCache.put(serverId, defaultFactory);
        return createServerFromFactory(defaultFactory, config, workspace);
    }

    /**
     * Get the serverId from a factory (factory.getServerId()).
     */
    protected abstract String getFactoryServerId(F factory);

    /**
     * Check if a factory can handle the config (factory.canHandle(config, workspace)).
     */
    protected abstract boolean canHandleConfig(F factory, C config, Workspace workspace);

    /**
     * Create a server from a factory (factory.createServer(...)).
     */
    protected abstract S createServerFromFactory(F factory, C config, Workspace workspace);

    /**
     * Get the default fallback factory.
     */
    protected abstract F getDefaultFactory();
}
