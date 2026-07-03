package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.dap.client.DapClient;
import com.redhat.mcp.languagetools.dap.client.DapEventListener;
import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.dap.transport.SocketTransportStreams;
import com.redhat.mcp.languagetools.dap.transport.StdioTransportStreams;
import com.redhat.mcp.languagetools.dap.transport.TransportStreams;
import com.redhat.mcp.languagetools.dap.transport.TransportType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    protected Integer allocatedPort; // Port allocated via ${port} substitution
    protected TransportStreams transportStreams; // Transport layer (stdio or socket)

    public DapServer(String sessionId, DapServerConfig config, Workspace workspace) {
        super(config, workspace, sessionId); // Pass sessionId for tracing instead of serverId
        this.sessionId = sessionId;
    }

    @Override
    protected TracingMessageConsumer.TraceCollectorAdd initializeTraceCollector(Workspace workspace) {
        DapTraceCollector collector = workspace.getApplication().getDapTraceCollector();
        // Pass the server reference so collector can check trace level
        collector.setServer(this);
        return collector;
    }

    /**
     * Get trace level from global config file (~/.mcp-languagetools/config.json).
     * Returns "off", "messages", or "verbose" (default).
     * Same behavior as LSP.
     */
    public String getTraceLevel() {
        return getTraceLevelFromConfig(getConfig().getServerId(), "dapServers");
    }

    /**
     * Shared utility to get trace level from config.
     * Can be used by both LSP and DAP servers.
     */
    private String getTraceLevelFromConfig(String serverId, String serverSection) {
        try {
            Path configFile = getWorkspace().getApplication().getPathManager().getGlobalConfigFile();
            if (!Files.exists(configFile)) {
                return "verbose"; // Default
            }

            String json = Files.readString(configFile);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            if (!root.has(serverSection)) {
                return "verbose";
            }

            com.google.gson.JsonObject servers = root.getAsJsonObject(serverSection);
            if (!servers.has(serverId)) {
                return "verbose";
            }

            com.google.gson.JsonObject serverConfig = servers.getAsJsonObject(serverId);
            if (!serverConfig.has("trace")) {
                return "verbose";
            }

            return serverConfig.get("trace").getAsString();
        } catch (Exception e) {
            LOG.debugf("Failed to read trace level from config: %s", e.getMessage());
            return "verbose"; // Default on error
        }
    }

    /**
     * Start the debug adapter process and create the launcher.
     * Does NOT send initialize request - that's done later in connectAndInitialize().
     */
    public CompletableFuture<Void> start() {
        // Common startup checks and preparation
        if (!checkAndPrepareStart()) {
            return CompletableFuture.completedFuture(null);
        }

        // Ensure server is installed first
        return withErrorLogging(
            ensureInstalled().thenCompose(v -> startServerProcess())
                .thenCompose(readyTracker -> waitForServerReady(readyTracker))
                .thenCompose(result -> createLauncher(result)),
            getTraceCollector(),
            sessionId
        );
    }

    /**
     * Start the DAP server process.
     */
    private CompletableFuture<DAPServerReadyTracker> startServerProcess() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var config = super.getConfig();
                setStatus(ServerStatus.STARTING);
                LOG.infof("Starting DAP server: %s", config.getName());

                // Build and launch process
                List<String> command = buildCommand();
                String commandStr = String.join(" ", command);
                LOG.debugf("DAP command: %s", commandStr);

                // Send startup traces
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

                // Set working directory to workspace root
                File workspaceDir = Paths.get(getWorkspace().getRootUri()).toFile();
                pb.directory(workspaceDir);

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

                getTraceCollector().addTrace(
                    workspaceRootUri,
                    sessionId,
                    TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                    String.format("DAP server process started (PID: %d)", serverProcess.pid())
                );

                // Create server ready tracker
                ServerReadyConfig readyConfig = config.getServerReadyConfig();
                if (allocatedPort != null && readyConfig.getPort() == null) {
                    readyConfig = new ServerReadyConfig("127.0.0.1", allocatedPort);
                }

                DAPServerReadyTracker readyTracker = new DAPServerReadyTracker(
                    readyConfig,
                    serverProcess,
                    line -> {
                        getTraceCollector().addTrace(
                            workspaceRootUri,
                            sessionId,
                            TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            line
                        );
                    }
                );

                // Start monitoring stderr for errors
                startStderrMonitoring(workspaceRootUri, sessionId);

                // Monitor process exit
                executorService.submit(() -> {
                    try {
                        while (serverProcess.isAlive()) {
                            if (serverProcess.waitFor(1, TimeUnit.SECONDS)) {
                                int exitCode = serverProcess.exitValue();
                                LOG.errorf("DAP server process exited with code %d: %s", exitCode, config.getName());
                                if (exitCode != 0) {
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

                return readyTracker;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    /**
     * Container for tracker and init args.
     */
    private static class ServerReadyResult {
        final DAPServerReadyTracker tracker;
        final InitializeRequestArguments initArgs;

        ServerReadyResult(DAPServerReadyTracker tracker, InitializeRequestArguments initArgs) {
            this.tracker = tracker;
            this.initArgs = initArgs;
        }
    }

    /**
     * Wait for the DAP server to be ready.
     */
    private CompletableFuture<ServerReadyResult> waitForServerReady(DAPServerReadyTracker readyTracker) {
        return readyTracker.track(executorService)
            .thenApply(v -> {
                LOG.infof("DAP server ready, creating launcher...");

                String workspaceRootUri = getWorkspace().getRootUri().toString();
                getTraceCollector().addTrace(
                    workspaceRootUri,
                    sessionId,
                    TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                    String.format("DAP server ready (address=%s, port=%s)",
                        readyTracker.getAddress(), readyTracker.getPort())
                );

                // Prepare InitializeRequestArguments
                InitializeRequestArguments initArgs = new InitializeRequestArguments();
                initArgs.setClientID("mcp-languagetools");
                initArgs.setClientName("MCP Language Tools");
                initArgs.setAdapterID(getConfig().getServerId());
                initArgs.setPathFormat("path");
                initArgs.setLinesStartAt1(true);
                initArgs.setColumnsStartAt1(true);

                // Declare support for reverse requests
                initArgs.setSupportsRunInTerminalRequest(true);
                initArgs.setSupportsStartDebuggingRequest(true);
                initArgs.setSupportsVariableType(true);
                initArgs.setSupportsVariablePaging(false);

                return new ServerReadyResult(readyTracker, initArgs);
            });
    }

    /**
     * Create the LSP4J launcher (but DON'T send initialize yet).
     * Like lsp4ij: just setup the transport and launcher, initialization happens in connectAndInitialize().
     */
    private CompletableFuture<Void> createLauncher(ServerReadyResult result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var config = getConfig();
                var tracker = result.tracker;
                var initArgs = result.initArgs;

                // Create transport streams
                // Like lsp4ij: if a port was detected/allocated, use SOCKET, else use STDIO
                String address = tracker.getAddress();
                Integer port = tracker.getPort();

                if (port != null) {
                    // Socket transport - server is listening on a port
                    String host = address != null ? address : "127.0.0.1";
                    LOG.infof("Creating socket transport to %s:%d", host, port);
                    transportStreams = new SocketTransportStreams(host, port);
                } else {
                    // Stdio transport - use process stdin/stdout
                    LOG.infof("Creating stdio transport (using process stdin/stdout)");
                    InputStream in = serverProcess.getInputStream();
                    OutputStream out = serverProcess.getOutputStream();
                    transportStreams = new StdioTransportStreams(in, out);
                }

                dapClient = new DapClient();

                // Wrapper for tracing
                Launcher<IDebugProtocolServer> launcher = DSPLauncher.createClientLauncher(
                        dapClient,
                        transportStreams.getInputStream(),
                        transportStreams.getOutputStream(),
                        executorService,
                        consumer -> message -> {
                            try {
                                getTracing().log(message, consumer);
                            } catch (Exception e) {
                                LOG.warnf(e, "Error tracing DAP message: %s", e.getMessage());
                            }
                            consumer.consume(message);
                        });

                debugServer = launcher.getRemoteProxy();
                launcher.startListening();

                LOG.infof("Launcher created and listening, ready for initialization");
                setStatus(ServerStatus.RUNNING);
                setReady(true);

                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    /**
     * Create a child launcher for a child debug session.
     * Like lsp4ij: each child session creates a NEW socket connection to the DAP server.
     * For socket transports, this creates a new Socket to the same port.
     * For stdio transports, child sessions are not supported.
     *
     * @param childClient the child DapClient that will receive events
     * @return the remote proxy for sending DAP requests
     */
    public IDebugProtocolServer createChildLauncher(DapClient childClient) {
        if (transportStreams == null) {
            throw new IllegalStateException("Transport streams not initialized");
        }

        LOG.infof("Creating child launcher with new transport connection");

        try {
            // Create NEW transport streams (like lsp4ij: new Socket to same port)
            TransportStreams childTransportStreams;

            if (transportStreams instanceof SocketTransportStreams) {
                // Extract host and port from parent socket
                SocketTransportStreams parentSocket = (SocketTransportStreams) transportStreams;
                String host = parentSocket.getSocket().getInetAddress().getHostAddress();
                int port = parentSocket.getSocket().getPort();

                LOG.infof("Creating new socket connection to %s:%d for child", host, port);
                childTransportStreams = new SocketTransportStreams(host, port);
            } else {
                throw new UnsupportedOperationException(
                    "Child sessions only supported for socket transport, not stdio");
            }

            // Create launcher with the NEW streams
            Launcher<IDebugProtocolServer> launcher = DSPLauncher.createClientLauncher(
                    childClient,
                    childTransportStreams.getInputStream(),
                    childTransportStreams.getOutputStream(),
                    executorService,
                    consumer -> message -> {
                        try {
                            getTracing().log(message, consumer);
                        } catch (Exception e) {
                            LOG.warnf(e, "Error tracing DAP message: %s", e.getMessage());
                        }
                        consumer.consume(message);
                    });

            // Start listening
            launcher.startListening();

            LOG.infof("Child launcher created and listening on new connection");
            return launcher.getRemoteProxy();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create child launcher", e);
        }
    }

    /**
     * Stop the debug adapter.
     * Closes transport, kills the process, and updates status.
     * Note: disconnect() should be called by the caller (DapSession) before this method.
     */
    public CompletableFuture<Void> stop2() {
        return CompletableFuture.runAsync(() -> {
            var config = super.getConfig();
            try {
                LOG.infof("Stopping DAP server: %s", config.getName());

                // Close transport streams first
                if (transportStreams != null) {
                    try {
                        transportStreams.close();
                    } catch (Exception e) {
                        LOG.debugf("Error closing transport streams (expected if already closed): %s", e.getMessage());
                    }
                }

                // Kill the server process
                if (serverProcess != null && serverProcess.isAlive()) {
                    LOG.infof("Destroying DAP server process (PID: %d)", serverProcess.pid());
                    serverProcess.destroy();
                    if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                        LOG.warnf("Server process did not terminate gracefully, forcing kill");
                        serverProcess.destroyForcibly();
                        serverProcess.waitFor(2, TimeUnit.SECONDS);
                    }
                    LOG.infof("DAP server process terminated");
                } else {
                    LOG.debugf("No server process to stop (already stopped or never started)");
                }

                setStatus(ServerStatus.STOPPED);
                setReady(false);
                LOG.infof("DAP server stopped: %s", config.getName());

            } catch (Exception e) {
                LOG.errorf(e, "Error stopping DAP server: %s", config.getServerId());
                // Still mark as stopped even if there was an error
                setStatus(ServerStatus.STOPPED);
                setReady(false);
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

        // Substitute ${port} if present - allocate a free port (for TCP mode DAP servers)
        Integer port = null;
        if (cmd.contains("${port}")) {
            port = getAvailablePort();
            cmd = cmd.replace("${port}", String.valueOf(port));
            LOG.infof("Allocated port %d for DAP server", port);

            // Store the port for later use in server ready tracking
            allocatedPort = port;
        }

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

    /**
     * Find an available TCP port for the DAP server.
     */
    private static int getAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            LOG.warnf("Failed to find available port, using default 4711: %s", e.getMessage());
            return 4711; // Fallback port
        }
    }


    // Getters

    public IDebugProtocolServer getDebugServer() {
        return debugServer;
    }

    public DapClient getDapClient() {
        return dapClient;
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
