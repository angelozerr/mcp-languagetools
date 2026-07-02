package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.dap.client.DapClient;
import com.redhat.mcp.languagetools.dap.client.DapEventListener;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.trace.TracingMessageConsumer;
import com.redhat.mcp.languagetools.server.ServerBase;
import com.redhat.mcp.languagetools.server.ServerStatus;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Debug Adapter Protocol (DAP) server wrapper.
 * Manages lifecycle of a DAP server (debug adapter) for a workspace.
 * Similar to LspServer but for debugging instead of language features.
 */
public class DapServer extends ServerBase<DapServerConfig> {

    private static final Logger LOG = Logger.getLogger(DapServer.class);
    private final String sessionId;

    protected IDebugProtocolServer debugServer;
    protected DapClient dapClient;

    public DapServer(String sessionId, DapServerConfig config, Workspace workspace) {
        super(config, workspace, sessionId); // Pass sessionId for tracing instead of serverId
        this.sessionId = sessionId;
    }

    @Override
    protected TracingMessageConsumer.TraceCollectorAdd initializeTraceCollector(Workspace workspace) {
        return workspace.getApplication().getDapTraceCollector();
    }

    /**
     * Start the debug adapter process and initialize it.
     */
    public CompletableFuture<Void> start() {
        // Common startup checks and preparation
        if (!checkAndPrepareStart()) {
            return CompletableFuture.completedFuture(null);
        }

        // Ensure server is installed first
        return withErrorLogging(
            ensureInstalled().thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                try {
                    var config = super.getConfig();
                    setStatus(ServerStatus.STARTING);
                    LOG.infof("Starting DAP server: %s", config.getName());

                    var serverHome = getServerHome();

                    // Build and launch process
                    List<String> command = buildCommand();
                    String commandStr = String.join(" ", command);
                    LOG.debugf("DAP command: %s", commandStr);

                    // Send startup traces (visible in UI) - separate lines, no folding
                    String workspaceRootUri = getWorkspace().getRootUri().toString();
                    getTraceCollector().addTrace(
                        workspaceRootUri,
                        sessionId,
                        TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                        String.format("Starting %s...", config.getName())
                    );
                    getTraceCollector().addTrace(
                        workspaceRootUri,
                        sessionId,
                        TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                        String.format("Command: %s", commandStr)
                    );

                    ProcessBuilder pb = new ProcessBuilder(command);

                    // Set working directory to workspace root (not serverHome)
                    File workspaceDir = Paths.get(getWorkspace().getRootUri()).toFile();
                    pb.directory(workspaceDir);

                    // Trace working directory (one line - no folding)
                    getTraceCollector().addTrace(
                        workspaceRootUri,
                        sessionId,
                        TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                        String.format("Working directory: %s", workspaceDir.toString())
                    );

                    // Set environment variables
                    if (config.getEnv() != null) {
                        config.getEnv().forEach((key, value) ->
                                pb.environment().put(key, value.toString()));
                    }

                    serverProcess = pb.start();
                    LOG.infof("DAP server process started: %s (PID: %d)",
                            config.getServerId(), serverProcess.pid());

                    // Trace server started (one line - no folding)
                    getTraceCollector().addTrace(
                        workspaceRootUri,
                        sessionId,
                        TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                        String.format("DAP server process started (PID: %d)", serverProcess.pid())
                    );

                    // Start monitoring stderr for errors
                    startStderrMonitoring(workspaceRootUri, sessionId);

                    // Monitor process exit (with periodic checks for interruption)
                    executorService.submit(() -> {
                        try {
                            while (serverProcess.isAlive()) {
                                // Wait max 1 second at a time, so interrupts work
                                if (serverProcess.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                                    // Process exited
                                    int exitCode = serverProcess.exitValue();
                                    LOG.errorf("DAP server process exited with code %d: %s", exitCode, config.getName());
                                    if (exitCode != 0) {
                                        // Use START_FAILED so the server can be restarted
                                        setStatus(ServerStatus.START_FAILED);
                                        setStatusMessage("Process exited with code " + exitCode);
                                        getTraceCollector().addTrace(
                                            workspaceRootUri,
                                            sessionId,
                                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                            String.format("[Error - %s] %s process exited with code %d",
                                                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                                                config.getName(),
                                                exitCode)
                                        );
                                    }
                                    break;
                                }
                                // Check if interrupted between waits
                                if (Thread.currentThread().isInterrupted()) {
                                    LOG.infof("Process monitor interrupted for %s", config.getName());
                                    break;
                                }
                            }
                        } catch (InterruptedException e) {
                            LOG.infof("Process monitor interrupted (exception) for %s", config.getName());
                            Thread.currentThread().interrupt();
                        }
                    });

                    // Create launcher with tracing
                    InputStream in = serverProcess.getInputStream();
                    OutputStream out = serverProcess.getOutputStream();

                    dapClient = new DapClient();

                    // Wrapper for tracing - use TracingMessageConsumer like LSP
                    Launcher<IDebugProtocolServer> launcher = DSPLauncher.createClientLauncher(
                            dapClient, in, out, executorService,
                            consumer -> message -> {
                                try {
                                    // Log the message using tracing (like LSP)
                                    getTracing().log(message, consumer);
                                } catch (Exception e) {
                                    LOG.warnf(e, "Error tracing DAP message: %s", e.getMessage());
                                }
                                // Forward to original consumer
                                consumer.consume(message);
                            });

                    debugServer = launcher.getRemoteProxy();
                    launcher.startListening();

                    // Prepare InitializeRequestArguments
                    InitializeRequestArguments initArgs = new InitializeRequestArguments();
                    initArgs.setClientID("mcp-languagetools");
                    initArgs.setClientName("MCP Language Tools");
                    initArgs.setAdapterID(config.getServerId());
                    initArgs.setPathFormat("path");
                    initArgs.setLinesStartAt1(true);
                    initArgs.setColumnsStartAt1(true);

                    return initArgs;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executorService)
            .thenCompose(initArgs ->
                debugServer.initialize(initArgs).thenApply(capabilities -> {
                    LOG.infof("DAP server initialized: %s", getConfig().getName());
                    setStatus(ServerStatus.RUNNING);
                    setReady(true);
                    return null;
                })
            )),
            getTraceCollector(),
            sessionId
        );
    }

    /**
     * Stop the debug adapter.
     */
    public CompletableFuture<Void> stop2() {
        return CompletableFuture.runAsync(() -> {
            var config = super.getConfig();
            try {
                LOG.infof("Stopping DAP server: %s", config.getName());

                if (debugServer != null) {
                    try {
                        debugServer.disconnect(new org.eclipse.lsp4j.debug.DisconnectArguments());
                    } catch (Exception e) {
                        LOG.warnf("Error during disconnect: %s", e.getMessage());
                    }
                }

                if (serverProcess != null && serverProcess.isAlive()) {
                    serverProcess.destroy();
                    if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                        serverProcess.destroyForcibly();
                    }
                }

                setStatus(ServerStatus.STOPPED);
                setReady(false);
                LOG.infof("DAP server stopped: %s", config.getName());

            } catch (Exception e) {
                LOG.errorf(e, "Error stopping DAP server: %s", config.getServerId());
            }
        }, executorService);
    }

    /**
     * Build the command to launch the debug adapter.
     */
    protected List<String> buildCommand() throws IOException {
        var config = super.getConfig();
        String cmd = config.getLaunchForCurrentOS();
        if (cmd == null) {
            throw new IOException("No launch command configured for current OS");
        }

        // TODO: Substitute ${port} if present - allocate a free port (for TCP mode DAP servers)
        // Integer port = null;
        // if (cmd.contains("${port}")) {
        //     port = getAvailablePort();
        //     cmd = cmd.replace("${port}", String.valueOf(port));
        //     LOG.infof("Allocated port %d for DAP server", port);
        // }

        // Substitute variables
        String resolved = cmd
                .replace("$SERVER_HOME$", getServerHome().toString());
                //.replace("${workspaceDataDir}", context.getWorkspaceDataDir().toString())
                //.replace("${context.getDapServerHome()}", context.getDapServerHome().toString());

        // Simple parsing (split by spaces, respecting quotes)
        List<String> command = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : resolved.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    command.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            command.add(current.toString());
        }

        LOG.infof("DAP command: %s", String.join(" ", command));
        return command;
    }

    // TODO: Uncomment when implementing TCP mode DAP servers
    // /**
    //  * Find an available TCP port for the DAP server.
    //  */
    // private static int getAvailablePort() {
    //     try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
    //         socket.setReuseAddress(true);
    //         return socket.getLocalPort();
    //     } catch (IOException e) {
    //         LOG.warnf("Failed to find available port, using default 4711: %s", e.getMessage());
    //         return 4711; // Fallback port
    //     }
    // }


    // Getters

    public IDebugProtocolServer getDebugServer() {
        return debugServer;
    }

    public Long getPid() {
        return serverProcess != null && serverProcess.isAlive()
                ? serverProcess.pid() : null;
    }

    // Setters

    /**
     * Set the event listener for DAP events (typically a DapSession).
     */
    public void setEventListener(DapEventListener listener) {
        if (dapClient != null) {
            dapClient.setEventListener(listener);
        }
    }

}
