/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.languagetools.extensions.jdtls.lsp;

import com.ibm.mcp.languagetools.ContributionManager;
import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerConfig;
import com.ibm.mcp.languagetools.progress.ProgressMonitor;
import com.ibm.mcp.languagetools.server.ServerConfigBase;
import com.ibm.mcp.languagetools.server.ServerConfigInstalledEvent;
import com.ibm.mcp.languagetools.trace.TraceCollector;
import com.ibm.mcp.languagetools.server.ServerConfigListener;
import com.ibm.mcp.languagetools.workspace.Workspace;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    public JdtLsServer(LspServerConfig config, Workspace workspace) {
        super(config, workspace);
    }

    @Override
    protected CompletableFuture<Void> ensureContributorsInstalled(ProgressMonitor progressMonitor) {
        var application = getWorkspace().getApplication();
        application.addServerConfigListener(this);

        ContributionManager.ContributionResult result = application
                .getContributionManager()
                .extractFilesFromContributionWithStatus(getId(), JdtLsContributes.BUNDLES);

        List<ServerConfigBase> contributors = result.getUninstalledContributors();
        if (contributors.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("Installing %d contributor(s) for JDT.LS", contributors.size());
        var pathManager = application.getPathManager();
        var traceCollector = getConfig().getTraceCollector();
        if (traceCollector != null && traceCollector.isEnabled()) {
            traceCollector.addTrace(getId(),
                    String.format("Installing %d contributor(s)...", contributors.size()),
                    TraceCollector.MessageType.INFO);
        }
        CompletableFuture<?>[] futures = contributors.stream()
                .map(contributor -> {
                    LOG.infof("Installing contributor '%s' bundles for JDT.LS", contributor.getServerId());
                    if (contributor.isContributionOnly() && contributor.getTraceCollector() == null && traceCollector != null) {
                        contributor.setTraceCollector(traceCollector);
                    }
                    return contributor.ensureInstalled(pathManager, null, progressMonitor)
                            .exceptionally(error -> {
                                LOG.warnf(error, "Failed to install contributor '%s' for JDT.LS, continuing without it",
                                        contributor.getServerId());
                                return null;
                            });
                })
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> {
                    LOG.infof("All %d contributor(s) installed for JDT.LS", contributors.size());
                    if (traceCollector != null && traceCollector.isEnabled()) {
                        traceCollector.addTrace(getId(),
                                String.format("All %d contributor(s) installed.", contributors.size()),
                                TraceCollector.MessageType.INFO);
                    }
                });
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

        // Contributors are already installed by ensureContributorsInstalled()
        List<String> bundlePaths = getWorkspace()
                .getApplication()
                .getContributionManager()
                .extractFilesFromContribution(getId(), JdtLsContributes.BUNDLES);

        if (!bundlePaths.isEmpty()) {
            options.put(JdtLsContributes.BUNDLES, bundlePaths);
            LOG.infof("Passing %d bundles to JDT.LS via initializationOptions", bundlePaths.size());
        }

        return options.isEmpty() ? null : options;
    }

    /**
     * Create a JDT.LS-specific language client that handles language/status notifications.
     */
    @Override
    protected LanguageClient createLanguageClient() {
        return new JdtLsLanguageClient(this);
    }

    void onServiceReady() {
        setReady(true);
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
     * Build the JDT.LS command with custom arguments.
     * Similar to vscode-java's prepareParams (javaServerStarter.ts).
     *
     * Command structure:
     * java [jvm-args] -jar launcher.jar [osgi-args] -configuration [config-dir] -data [workspace]
     */
    @Override
    protected List<String> buildCommand() throws IOException {
        List<String> params = new ArrayList<>();

        // 1. Java executable
        String javaHome = System.getProperty("java.home");
        String javaBin = Paths.get(javaHome, "bin", "java").toString();
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
        params.add(getJdtlsDataDir().toString());

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
        List<String> parsedArgs = parseVMArgsString(vmargs);
        params.addAll(parsedArgs);

        LOG.infof("Added VM args from java.jdt.ls.vmargs: %s", vmargs);
    }

    /**
     * Parse VM arguments string into a list.
     * Handles quotes: "arg with spaces" or -Dfoo="bar baz"
     */
    private List<String> parseVMArgsString(String vmargs) {
        List<String> result = new ArrayList<>();
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
    private void addDefaultVMArgsIfMissing(List<String> params) {
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
    private void addLauncherJar(List<String> params) throws IOException {
        Path pluginsDir = getServerHome().resolve("plugins");

        try (var files = Files.walk(pluginsDir, 1)) {
            var launcher = files
                .filter(p -> p.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .findFirst();

            if (launcher.isPresent()) {
                params.add("-jar");
                params.add(launcher.get().toString());
            } else {
                throw new IOException("Could not find Eclipse Equinox launcher JAR in " + pluginsDir);
            }
        }
    }

    /**
     * Get the configuration directory based on OS.
     * Similar to vscode-java's configDir selection (no syntax server support).
     */
    private Path getConfigurationDirectory() {
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

    private Path getJdtlsDataDir() {
        URI rootUri = getWorkspace().getRootUri();
        String workspaceName = Path.of(rootUri).getFileName().toString();
        Path baseDir = getWorkspace().getApplication().getPathManager().getMcpLangToolsRoot().resolve("jdtls-workspaces");
        Path dir = baseDir.resolve(workspaceName + "-" + Math.abs(rootUri.hashCode()));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create JDT.LS data directory", e);
        }
        return dir;
    }
}
