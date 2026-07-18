package com.ibm.mcp.languagetools.server;

import com.ibm.mcp.languagetools.Application;
import com.ibm.mcp.languagetools.dap.server.DapServerConfig;
import com.ibm.mcp.languagetools.dap.server.DapServerDescriptorLoader;
import com.ibm.mcp.languagetools.lsp.server.LspServerConfig;
import com.ibm.mcp.languagetools.lsp.server.LspServerDescriptorLoader;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry that discovers and loads all server configurations (LSP and DAP).
 * Scans resource directories once and delegates to appropriate loaders.
 */
@ApplicationScoped
public class ServerDescriptorRegistry {

    private static final Logger LOG = Logger.getLogger(ServerDescriptorRegistry.class);

    private final Map<String, ServerDescriptorLoaderBase<?>> loaders = new HashMap<>();

    @Inject
    LspServerDescriptorLoader lspServerDescriptorLoader;

    @Inject
    DapServerDescriptorLoader dapServerDescriptorLoader;

    void onStart(@Observes @Priority(1) StartupEvent ev) {
        LOG.info("ServerDescriptorRegistry starting...");
        // Register loaders on startup
        registerLoader(lspServerDescriptorLoader);
        registerLoader(dapServerDescriptorLoader);
        LOG.infof("ServerDescriptorRegistry initialized with %d loaders", loaders.size());
    }

    /**
     * Register a loader for a specific root directory.
     */
    public <T extends ServerConfigBase> void registerLoader(ServerDescriptorLoaderBase<T> loader) {
        String root = loader.getRoot();
        loaders.put(root, loader);
        LOG.infof("Registered loader for root: %s", root);
    }

    /**
     * Load all bundled servers by scanning all registered roots.
     * Returns combined map of all server configs (LSP + DAP).
     */
    public Map<String, ServerConfigBase> loadAllBundled(Application application) {
        Map<String, ServerConfigBase> allConfigs = new HashMap<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            LOG.infof("Using ClassLoader: %s", classLoader.getClass().getName());

            // Scan each registered root
            for (String root : loaders.keySet()) {
                LOG.infof("Scanning for servers in root: %s", root);
                Enumeration<URL> resources = classLoader.getResources(root);

                int resourceCount = 0;
                while (resources.hasMoreElements()) {
                    URL dirUrl = resources.nextElement();
                    resourceCount++;
                    LOG.infof("Found resource #%d for root '%s': %s", resourceCount, root, dirUrl);
                    scanDirectory(dirUrl, root, allConfigs, application);
                }

                if (resourceCount == 0) {
                    LOG.warnf("No resources found for root: %s", root);
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to load bundled servers");
        }

        LOG.infof("Loaded %d total servers", allConfigs.size());
        return allConfigs;
    }

    /**
     * Load LSP servers only.
     */
    public Map<String, LspServerConfig> loadAllLspServers(Application application) {
        Map<String, LspServerConfig> lspConfigs = new HashMap<>();
        Map<String, ServerConfigBase> all = loadAllBundled(application);

        for (Map.Entry<String, ServerConfigBase> entry : all.entrySet()) {
            if (entry.getValue() instanceof LspServerConfig) {
                lspConfigs.put(entry.getKey(), (LspServerConfig) entry.getValue());
            }
        }

        return lspConfigs;
    }

    /**
     * Load DAP servers only.
     */
    public Map<String, DapServerConfig> loadAllDapServers(Application application) {
        Map<String, DapServerConfig> dapConfigs = new HashMap<>();
        Map<String, ServerConfigBase> all = loadAllBundled(application);

        for (Map.Entry<String, ServerConfigBase> entry : all.entrySet()) {
            if (entry.getValue() instanceof DapServerConfig) {
                dapConfigs.put(entry.getKey(), (DapServerConfig) entry.getValue());
            }
        }

        return dapConfigs;
    }

    private void scanDirectory(URL dirUrl, String root, Map<String, ServerConfigBase> configs, Application application) {
        try {
            LOG.infof("Scanning directory URL: %s for root: %s", dirUrl, root);
            URI dirUri = dirUrl.toURI();
            Path dirPath;
            FileSystem fs = null;

            if (dirUri.getScheme().equals("jar")) {
                // Running from JAR
                try {
                    fs = FileSystems.newFileSystem(dirUri, Collections.emptyMap());
                    dirPath = fs.getPath("/" + root);
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(dirUri);
                    dirPath = fs.getPath("/" + root);
                }
            } else {
                // Running from filesystem
                dirPath = Paths.get(dirUri);
            }

            LOG.infof("Scanning directory path: %s", dirPath);

            // Scan for server directories
            try (var entries = Files.list(dirPath)) {
                entries.filter(Files::isDirectory)
                       .forEach(serverDir -> {
                           String serverId = serverDir.getFileName().toString();
                           LOG.infof("Found server directory: %s", serverId);

                           // Skip if already loaded (first one wins)
                           if (configs.containsKey(serverId)) {
                               LOG.debugf("Skipping duplicate server: %s", serverId);
                               return;
                           }

                           // Load using appropriate loader
                           ServerDescriptorLoaderBase<?> loader = loaders.get(root);
                           if (loader != null) {
                               try {
                                   ServerConfigBase config = loader.loadBundled(serverDir, application);
                                   configs.put(serverId, config);
                                   LOG.infof("Loaded server: %s from %s", serverId, root);
                               } catch (Exception e) {
                                   LOG.errorf(e, "Failed to load server %s", serverId);
                               }
                           } else {
                               LOG.warnf("No loader found for root: %s", root);
                           }
                       });
            }

        } catch (Exception e) {
            LOG.warnf(e, "Failed to scan directory: %s", dirUrl);
        }
    }
}
