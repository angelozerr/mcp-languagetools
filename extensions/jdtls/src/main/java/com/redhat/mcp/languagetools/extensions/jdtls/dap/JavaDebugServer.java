package com.redhat.mcp.languagetools.extensions.jdtls.dap;

import com.redhat.mcp.languagetools.dap.client.DapClient;
import com.redhat.mcp.languagetools.dap.server.DapServer;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.server.ServerStatus;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Java Debug Server implementation that uses JDTLS for embedded debugging.
 *
 * <p>This server doesn't launch an external process. Instead, it:</p>
 * <ol>
 *   <li>Calls JDTLS commands to resolve classpath, java executable, etc.</li>
 *   <li>Calls vscode.java.startDebugSession to load the java-debug bundle in JDTLS</li>
 *   <li>Connects to the returned port</li>
 * </ol>
 */
public class JavaDebugServer extends DapServer {

    private static final Logger LOG = Logger.getLogger(JavaDebugServer.class);

    // JDTLS commands
    private static final String CMD_VALIDATE_LAUNCH_CONFIG = "vscode.java.validateLaunchConfig";
    private static final String CMD_BUILD_WORKSPACE = "vscode.java.buildWorkspace";
    private static final String CMD_RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";
    private static final String CMD_RESOLVE_JAVA_EXECUTABLE = "vscode.java.resolveJavaExecutable";
    private static final String CMD_START_DEBUG_SESSION = "vscode.java.startDebugSession";

    public JavaDebugServer(String sessionId, DapServerConfig config, Workspace workspace) {
        super(sessionId, config, workspace);
    }

    /**
     * Override to create JavaDebugClient instead of base DapClient.
     */
    @Override
    protected DapClient createDapClient() {
        return new JavaDebugClient();
    }

    /**
     * Override to create child JavaDebugClient.
     */
    @Override
    public DapClient createDapClient(DapClient parentClient) {
        return new JavaDebugClient((JavaDebugClient) parentClient);
    }

    @Override
    public CompletableFuture<Void> start() {
        // Common startup checks
        if (!checkAndPrepareStart()) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("Starting Java Debug Server (embedded mode via JDTLS)");
        setStatus(ServerStatus.STARTING);

        // For embedded mode, we don't start a process
        // The debug adapter runs inside JDTLS and is started when we call vscode.java.startDebugSession
        // So we just mark as RUNNING immediately
        setStatus(ServerStatus.RUNNING);
        setReady(true);

        return CompletableFuture.completedFuture(null);
    }


    /**
     * Override enrichLaunchConfiguration to add Java-specific resolution.
     * This is called by DapSession.launch() before connecting to the debug adapter.
     */
    @Override
    public CompletableFuture<Map<String, Object>> enrichLaunchConfiguration(
            Map<String, Object> launchConfig,
            String sessionId) {

        LOG.infof("Enriching launch configuration for Java");

        // Resolve classpath, java executable, etc. via JDTLS commands
        return resolveLaunchConfiguration(launchConfig, sessionId)
                .thenCompose(enrichedConfig -> {
                    // Start debug session (loads java-debug bundle in JDTLS and returns port)
                    return startDebugSession(sessionId)
                            .thenCompose(port -> {
                                String workspaceRootUri = getWorkspace().getRootUri().toString();

                                getTraceCollector().addTrace(
                                        workspaceRootUri,
                                        sessionId,
                                        TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                                        String.format("Connecting to DAP server on port %d...", port)
                                );

                                // Use DapServer's connectToSocket() - much simpler!
                                return connectToSocket("localhost", port)
                                        .thenApply(v -> {
                                            getTraceCollector().addTrace(
                                                    workspaceRootUri,
                                                    sessionId,
                                                    TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                                    String.format("Connected to DAP server on port %d", port)
                                            );
                                            return enrichedConfig;
                                        });
                            });
                });
    }

    /**
     * Resolve launch configuration by calling JDTLS commands.
     * This enriches the launch config with classPaths, modulePaths, javaExec, etc.
     *
     * @param launchConfig The initial launch configuration
     * @param sessionId The session ID for tracing
     * @return Enriched launch configuration
     */
    public CompletableFuture<Map<String, Object>> resolveLaunchConfiguration(
            Map<String, Object> launchConfig,
            String sessionId) {

        String mainClass = (String) launchConfig.get("mainClass");
        String projectName = (String) launchConfig.get("projectName");
        String workspaceRootUri = getWorkspace().getRootUri().toString();

        LOG.infof("Resolving launch configuration for mainClass=%s, projectName=%s", mainClass, projectName);

        // Step 1: Validate launch config
        return validateLaunchConfig(workspaceRootUri, mainClass, projectName, sessionId)
                .thenCompose(validation -> {
                    // Step 2: Build workspace if needed
                    return buildWorkspace(mainClass, sessionId);
                })
                .thenCompose(buildResult -> {
                    // Step 3: Resolve classpath
                    return resolveClasspath(mainClass, projectName, sessionId);
                })
                .thenCompose(classpaths -> {
                    // Step 4: Resolve java executable
                    return resolveJavaExecutable(mainClass, projectName, sessionId)
                            .thenApply(javaExec -> {
                                // Enrich launch config
                                launchConfig.put("modulePaths", classpaths.get(0)); // modulePaths
                                launchConfig.put("classPaths", classpaths.get(1));  // classPaths
                                launchConfig.put("javaExec", javaExec);

                                LOG.infof("Launch configuration resolved: classPaths=%d entries, javaExec=%s",
                                        ((List<?>) classpaths.get(1)).size(), javaExec);

                                return launchConfig;
                            });
                })
                .exceptionally(ex -> {
                    String error = String.format("Failed to resolve launch configuration: %s", ex.getMessage());
                    LOG.error(error, ex);
                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("ERROR resolving launch config: %s", ex.getMessage())
                    );
                    throw new RuntimeException(error, ex);
                });
    }

    /**
     * Start the debug session by calling vscode.java.startDebugSession.
     * This loads the java-debug bundle in JDTLS and returns the port.
     *
     * @param sessionId The session ID for tracing
     * @return The port where the debug adapter is listening
     */
    public CompletableFuture<Integer> startDebugSession(String sessionId) {
        String workspaceRootUri = getWorkspace().getRootUri().toString();

        getTraceCollector().addTrace(
                workspaceRootUri,
                sessionId,
                TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                String.format("Calling %s...", CMD_START_DEBUG_SESSION)
        );

        // Call vscode.java.startDebugSession via bindRequest mechanism
        return request(CMD_START_DEBUG_SESSION, List.of())
                .thenApply(result -> {
                    if (!(result instanceof Number)) {
                        String error = String.format("%s returned non-numeric result: %s", CMD_START_DEBUG_SESSION, result);
                        LOG.error(error);
                        throw new IllegalStateException(error);
                    }

                    int port = ((Number) result).intValue();
                    LOG.infof("Debug adapter listening on port: %d", port);

                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("Debug adapter listening on port: %d", port)
                    );

                    return port;
                });
    }

    // ===== Private helper methods for JDTLS commands =====

    private CompletableFuture<Object> validateLaunchConfig(
            String workspaceRootUri,
            String mainClass,
            String projectName,
            String sessionId) {

        List<Object> args = List.of(workspaceRootUri, mainClass, projectName, false);

        getTraceCollector().addTrace(
                workspaceRootUri,
                sessionId,
                TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                String.format("Calling %s...", CMD_VALIDATE_LAUNCH_CONFIG)
        );

        return request(CMD_VALIDATE_LAUNCH_CONFIG, args)
                .handle((result, ex) -> {
                    if (ex != null) {
                        String error = String.format("Error calling %s: %s", CMD_VALIDATE_LAUNCH_CONFIG, ex.getMessage());
                        LOG.error(error, ex);
                        getTraceCollector().addTrace(
                                workspaceRootUri,
                                sessionId,
                                TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                String.format("ERROR %s: %s", CMD_VALIDATE_LAUNCH_CONFIG, ex.getMessage())
                        );
                        throw new RuntimeException(error, ex);
                    }
                    LOG.debugf("Launch config validation result: %s", result);
                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("%s: OK", CMD_VALIDATE_LAUNCH_CONFIG)
                    );
                    return result;
                });
    }

    private CompletableFuture<Object> buildWorkspace(String mainClass, String sessionId) {
        // Build workspace expects a JSON string (not a structured object)
        String buildArg = String.format("{\"mainClass\":\"%s\",\"isFullBuild\":false}", mainClass);
        String workspaceRootUri = getWorkspace().getRootUri().toString();

        getTraceCollector().addTrace(
                workspaceRootUri,
                sessionId,
                TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                String.format("Calling %s...", CMD_BUILD_WORKSPACE)
        );

        return request(CMD_BUILD_WORKSPACE, List.of(buildArg))
                .handle((result, ex) -> {
                    if (ex != null) {
                        String error = String.format("Error calling %s: %s", CMD_BUILD_WORKSPACE, ex.getMessage());
                        LOG.error(error, ex);
                        getTraceCollector().addTrace(
                                workspaceRootUri,
                                sessionId,
                                TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                String.format("ERROR %s: %s", CMD_BUILD_WORKSPACE, ex.getMessage())
                        );
                        throw new RuntimeException(error, ex);
                    }
                    LOG.debugf("Build workspace result: %s", result);
                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("%s: OK", CMD_BUILD_WORKSPACE)
                    );
                    return result;
                });
    }

    private CompletableFuture<List<List<String>>> resolveClasspath(
            String mainClass,
            String projectName,
            String sessionId) {

        // Use Arrays.asList instead of List.of because List.of doesn't allow null values
        List<Object> args = java.util.Arrays.asList(mainClass, projectName, null);
        String workspaceRootUri = getWorkspace().getRootUri().toString();

        getTraceCollector().addTrace(
                workspaceRootUri,
                sessionId,
                TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                String.format("Calling %s...", CMD_RESOLVE_CLASSPATH)
        );

        return request(CMD_RESOLVE_CLASSPATH, args)
                .handle((result, ex) -> {
                    if (ex != null) {
                        String error = String.format("Error calling %s: %s", CMD_RESOLVE_CLASSPATH, ex.getMessage());
                        LOG.error(error, ex);
                        getTraceCollector().addTrace(
                                workspaceRootUri,
                                sessionId,
                                TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                String.format("ERROR %s: %s", CMD_RESOLVE_CLASSPATH, ex.getMessage())
                        );
                        throw new RuntimeException(error, ex);
                    }
                    @SuppressWarnings("unchecked")
                    List<List<String>> classpaths = (List<List<String>>) result;
                    LOG.debugf("Resolved classpath: modulePaths=%d, classPaths=%d",
                            classpaths.get(0).size(), classpaths.get(1).size());
                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("%s: %d modulePaths, %d classPaths",
                                    CMD_RESOLVE_CLASSPATH, classpaths.get(0).size(), classpaths.get(1).size())
                    );
                    return classpaths;
                });
    }

    private CompletableFuture<String> resolveJavaExecutable(
            String mainClass,
            String projectName,
            String sessionId) {

        List<Object> args = List.of(mainClass, projectName);
        String workspaceRootUri = getWorkspace().getRootUri().toString();

        getTraceCollector().addTrace(
                workspaceRootUri,
                sessionId,
                TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                String.format("Calling %s...", CMD_RESOLVE_JAVA_EXECUTABLE)
        );

        return request(CMD_RESOLVE_JAVA_EXECUTABLE, args)
                .handle((result, ex) -> {
                    if (ex != null) {
                        String error = String.format("Error calling %s: %s", CMD_RESOLVE_JAVA_EXECUTABLE, ex.getMessage());
                        LOG.error(error, ex);
                        getTraceCollector().addTrace(
                                workspaceRootUri,
                                sessionId,
                                TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                String.format("ERROR %s: %s", CMD_RESOLVE_JAVA_EXECUTABLE, ex.getMessage())
                        );
                        throw new RuntimeException(error, ex);
                    }
                    String javaExec = (String) result;
                    LOG.debugf("Resolved java executable: %s", javaExec);
                    getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            String.format("%s: %s", CMD_RESOLVE_JAVA_EXECUTABLE, javaExec)
                    );
                    return javaExec;
                });
    }
}
