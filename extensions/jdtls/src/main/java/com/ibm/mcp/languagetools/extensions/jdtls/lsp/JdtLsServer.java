package com.ibm.mcp.languagetools.extensions.jdtls.lsp;

import com.ibm.mcp.languagetools.ContributionManager;
import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerConfig;
import com.ibm.mcp.languagetools.progress.ProgressMonitor;
import com.ibm.mcp.languagetools.server.ServerConfigBase;
import com.ibm.mcp.languagetools.server.ServerConfigInstalledEvent;
import com.ibm.mcp.languagetools.server.ServerConfigListener;
import com.ibm.mcp.languagetools.workspace.Workspace;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Custom LSP server for Eclipse JDT.LS.
 * Handles JDT.LS-specific startup logic and readiness detection.
 * Similar to vscode-java's javaServerStarter.ts
 */
public class JdtLsServer extends LspServer implements ServerConfigListener {

    private static final Logger LOG = Logger.getLogger(JdtLsServer.class);

    private JdtLsLanguageClient jdtClient;

    private CompletableFuture<Void> pendingBundleInstallations;
    private List<ServerConfigBase> uninstalledContributors;

    public JdtLsServer(LspServerConfig config, Workspace workspace) {
        super(config, workspace);
    }

    /**
     * Prepare initialization options for JDT.LS.
     * Collects bundles from contributes.jdtls and passes them via initializationOptions.bundles.
     */
    @Override
    protected Object prepareInitializationOptions() {
        Map<String, Object> options = new HashMap<>();

        // Start with config-defined initialization options
        var config = super.getConfig();
        if (config.getInitializationOptions() != null && !config.getInitializationOptions().isEmpty()) {
            options.putAll(config.getInitializationOptions());
        }

        // Add required JDT.LS settings if not already present
        if (!options.containsKey("settings")) {
            Map<String, Object> settings = new HashMap<>();
            Map<String, Object> javaSettings = new HashMap<>();
            settings.put("java", javaSettings);
            options.put("settings", settings);
        }

        // Add extended client capabilities
        if (!options.containsKey("extendedClientCapabilities")) {
            Map<String, Object> extendedCaps = new HashMap<>();
            extendedCaps.put("classFileContentsSupport", true);
            extendedCaps.put("shouldLanguageServerExitOnShutdown", true);
            options.put("extendedClientCapabilities", extendedCaps);
        }

        // Collect bundles and detect uninstalled contributors in a single pass
        ContributionManager.ContributionResult result = getWorkspace()
                .getApplication()
                .getContributionManager()
                .extractFilesFromContributionWithStatus(getId(), JdtLsContributes.BUNDLES);

        List<String> bundlePaths = result.getResolvedFiles();
        if (!bundlePaths.isEmpty()) {
            options.put(JdtLsContributes.BUNDLES, bundlePaths);
            LOG.infof("Passing %d bundles to JDT.LS via initializationOptions", bundlePaths.size());
        }

        // Start installing contributors whose bundles are not yet on disk
        List<ServerConfigBase> contributors = result.getUninstalledContributors();
        if (!contributors.isEmpty()) {
            uninstalledContributors = contributors;
            var application = getWorkspace().getApplication();
            application.addServerConfigListener(this);
            var pathManager = application.getPathManager();
            CompletableFuture<?>[] futures = contributors.stream()
                    .map(contributor -> {
                        LOG.infof("Starting background installation of '%s' bundles for JDT.LS", contributor.getServerId());
                        return contributor.ensureInstalled(pathManager, null, ProgressMonitor.none());
                    })
                    .toArray(CompletableFuture[]::new);
            pendingBundleInstallations = CompletableFuture.allOf(futures);
        }

        return options.isEmpty() ? null : options;
    }

    /**
     * Create a JDT.LS-specific language client that handles language/status notifications.
     */
    @Override
    protected LanguageClient createLanguageClient() {
        jdtClient = new JdtLsLanguageClient(this);
        return jdtClient;
    }

    /**
     * Called when JDT.LS reports ServiceReady.
     * If bundle installations are pending, waits for them to complete
     * before setting ready (reloadBundles is handled by the install listener).
     */
    void onServiceReady() {
        if (pendingBundleInstallations == null) {
            setReady(true);
            return;
        }
        pendingBundleInstallations
                .thenCompose(v -> reloadBundles())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOG.errorf(error, "Failed to install/reload contributor bundles for JDT.LS");
                    }
                    pendingBundleInstallations = null;
                    uninstalledContributors = null;
                    setReady(true);
                });
    }

    @Override
    public void onInstalled(ServerConfigInstalledEvent event) {
        ServerConfigBase config = event.getConfig();
        if (!hasJdtLsBundles(config)) {
            return;
        }
        if (!isReady()) {
            return;
        }
        LOG.infof("Contributor '%s' installed while JDT.LS is ready, reloading bundles", config.getServerId());
        reloadBundles()
                .exceptionally(error -> {
                    LOG.errorf(error, "Failed to reload bundles into JDT.LS");
                    return null;
                });
    }

    private boolean hasJdtLsBundles(ServerConfigBase config) {
        var contributes = config.getContributes();
        if (contributes == null) {
            return false;
        }
        var jdtls = contributes.getContribution(getId());
        if (jdtls == null || !jdtls.isJsonObject()) {
            return false;
        }
        return jdtls.getAsJsonObject().has(JdtLsContributes.BUNDLES);
    }

    private CompletableFuture<Object> reloadBundles() {
        List<String> bundlePaths = getWorkspace()
                .getApplication()
                .getContributionManager()
                .extractFilesFromContribution(getId(), JdtLsContributes.BUNDLES);
        if (bundlePaths.isEmpty()) {
            LOG.warn("No bundles found, skipping java.reloadBundles");
            return CompletableFuture.completedFuture(null);
        }
        LOG.infof("Reloading %d bundles into JDT.LS via java.reloadBundles", bundlePaths.size());
        return sendCommandRequest("java.reloadBundles", List.of(bundlePaths));
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return super.initialize()
                .thenRun(() -> {
                    // Override parent behavior: JDT.LS is NOT ready immediately after initialize
                    // We need to wait for language/status notification with "ServiceReady"
                    setReady(false);
                    LOG.infof("JDT.LS initialized for workspace: %s (waiting for ServiceReady notification)", workspaceRoot);
                    // The language/status notifications will be handled by JdtLsLanguageClient
                });
    }

    /**
     * Get the JDT.LS-specific client.
     */
    public JdtLsLanguageClient getJdtClient() {
        return jdtClient;
    }

    /**
     * Build the JDT.LS command with custom arguments.
     * Similar to vscode-java's prepareParams (javaServerStarter.ts).
     *
     * Command structure:
     * java [jvm-args] -jar launcher.jar [osgi-args] -configuration [config-dir] -data [workspace]
     */
    @Override
    protected java.util.List<String> buildCommand() throws java.io.IOException {
        java.util.List<String> params = new java.util.ArrayList<>();

        // 1. Java executable
        String javaHome = System.getProperty("java.home");
        String javaBin = java.nio.file.Paths.get(javaHome, "bin", "java").toString();
        params.add(javaBin);

        // 2. Java module system arguments (required for Java 9+)
        params.add("--add-modules=ALL-SYSTEM");
        params.add("--add-opens");
        params.add("java.base/java.util=ALL-UNNAMED");
        params.add("--add-opens");
        params.add("java.base/java.lang=ALL-UNNAMED");
        params.add("--add-opens");
        params.add("java.base/sun.nio.fs=ALL-UNNAMED");

        // 3. VM arguments from config (e.g., heap size)
        addVMArgs(params);

        // 4. Default arguments if not already present
        addDefaultVMArgsIfMissing(params);

        // 5. Find and add launcher JAR
        addLauncherJar(params);

        // 6. Eclipse/OSGi configuration directory
        params.add("-configuration");
        params.add(getConfigurationDirectory().toString());

        // 7. Eclipse application parameters
        params.add("-Declipse.application=org.eclipse.jdt.ls.core.id1");
        params.add("-Dosgi.bundles.defaultStartLevel=4");
        params.add("-Declipse.product=org.eclipse.jdt.ls.core.product");

        // 9. Workspace data directory
        params.add("-data");
        params.add(workspaceDataDir.toString());

        LOG.infof("JDT.LS command: %s", String.join(" ", params));
        return params;
    }

    /**
     * Add VM arguments from workspace configuration (java.jdt.ls.vmargs).
     * Similar to vscode-java's parseVMargs().
     */
    private void addVMArgs(List<String> params) {
        var workspaceConfiguration = getWorkspace().getConfiguration();
        if (workspaceConfiguration == null) {
            LOG.debug("No workspace configuration available, skipping vmargs");
            return;
        }

        String vmargs = workspaceConfiguration.getString("java.jdt.ls.vmargs");
        if (vmargs == null || vmargs.trim().isEmpty()) {
            LOG.debug("No java.jdt.ls.vmargs configured");
            return;
        }

        // Parse vmargs string - handle quoted arguments
        java.util.List<String> parsedArgs = parseVMArgsString(vmargs);
        params.addAll(parsedArgs);

        LOG.infof("Added VM args from java.jdt.ls.vmargs: %s", vmargs);
    }

    /**
     * Parse VM arguments string into a list.
     * Handles quotes: "arg with spaces" or -Dfoo="bar baz"
     */
    private java.util.List<String> parseVMArgsString(String vmargs) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < vmargs.length(); i++) {
            char c = vmargs.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    /**
     * Add default VM arguments if not already present.
     */
    private void addDefaultVMArgsIfMissing(java.util.List<String> params) {
        String paramsStr = String.join(" ", params);

        // Disable VM installations detection job
        if (!paramsStr.contains("-DDetectVMInstallationsJob.disabled")) {
            params.add("-DDetectVMInstallationsJob.disabled=true");
        }

        // File encoding (default to UTF-8)
        if (!paramsStr.contains("-Dfile.encoding")) {
            params.add("-Dfile.encoding=UTF-8");
        }

        // Disable JVM logging
        if (!paramsStr.contains("-Xlog")) {
            params.add("-Xlog:disable");
        }

        // Default heap size if not specified
        if (!paramsStr.contains("-Xmx")) {
            params.add("-Xmx1G");
        }
        if (!paramsStr.contains("-Xms")) {
            params.add("-Xms100m");
        }
    }

    /**
     * Find the Eclipse Equinox launcher JAR and add to params.
     */
    private void addLauncherJar(java.util.List<String> params) throws java.io.IOException {
        java.nio.file.Path pluginsDir = getServerHome().resolve("plugins");

        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(pluginsDir, 1)) {
            java.util.Optional<java.nio.file.Path> launcher = files
                .filter(p -> p.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .findFirst();

            if (launcher.isPresent()) {
                params.add("-jar");
                params.add(launcher.get().toString());
            } else {
                throw new java.io.IOException("Could not find Eclipse Equinox launcher JAR in " + pluginsDir);
            }
        }
    }

    /**
     * Get the configuration directory based on OS.
     * Similar to vscode-java's configDir selection (no syntax server support).
     */
    private java.nio.file.Path getConfigurationDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String configDir;

        if (os.contains("win")) {
            configDir = "config_win";
        } else if (os.contains("mac")) {
            configDir = "config_mac";
        } else {
            configDir = "config_linux";
        }

        return getServerHome().resolve(configDir);
    }
}
