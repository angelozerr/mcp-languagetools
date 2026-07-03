package com.redhat.mcp.languagetools.dap.session;

import com.redhat.mcp.languagetools.dap.client.DapClient;
import com.redhat.mcp.languagetools.dap.client.DapEventListener;
import com.redhat.mcp.languagetools.dap.server.DapServer;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single debug session.
 * One session = one program being debugged via DAP.
 *
 * Lifecycle:
 * 1. create() - Create session with language/name
 * 2. setBreakpoint() - Set breakpoints before launch
 * 3. launch() or attach() - Start debugging
 * 4. step/continue/evaluate operations
 * 5. terminate() - End session
 */
public class DapSession implements DapEventListener {

    private static final Logger LOG = Logger.getLogger(DapSession.class);

    private final String sessionId;
    private final String language;
    private final String sessionName;
    private final DapServerConfig serverConfig;
    private final DapServer dapServer; // Not final - can be recreated after error
    private final Workspace workspace;
    private final DapTraceCollector traceCollector;

    private SessionState state = SessionState.CREATED;
    private boolean debugMode = false; // Default to run mode (without debugging)
    private Runnable stateChangeCallback; // Callback when session state changes
    private final Map<String, BreakpointInfo> breakpoints = new ConcurrentHashMap<>();
    private Thread[] threads = new Thread[0];
    private StackFrame[] currentStackFrames = new StackFrame[0];
    private Integer currentThreadId;

    // Parent-child session support
    private final List<DapSession> childSessions = new ArrayList<>();

    public enum SessionState {
        CREATED,
        RUNNING,
        PAUSED,
        TERMINATED,
        ERROR
    }

    public static class BreakpointInfo {
        public final String breakpointId;
        public final String file;
        public final int line;
        public final String condition;
        public boolean verified;
        public Breakpoint dapBreakpoint;

        public BreakpointInfo(String breakpointId, String file, int line, String condition) {
            this.breakpointId = breakpointId;
            this.file = file;
            this.line = line;
            this.condition = condition;
            this.verified = false;
        }
    }

    public DapSession(String sessionId,
                      String language,
                      String sessionName,
                      DapServerConfig serverConfig,
                      Workspace workspace,
                      DapTraceCollector traceCollector) {
        this.sessionId = sessionId;
        this.language = language;
        this.sessionName = sessionName;
        this.serverConfig = serverConfig;
        this.workspace = workspace;
        this.traceCollector = workspace.getApplication().getDapTraceCollector();

        // Update the trace collector wrapper with the correct sessionId for installation traces
        if (serverConfig.getTraceCollector() instanceof DapTraceCollectorWrapper) {
            // Replace with a new wrapper that uses the session's sessionId
            serverConfig.setTraceCollector(new DapTraceCollectorWrapper(
                workspace.getApplication().getDapTraceCollector(),
                workspace.getRootUri().toString(),
                sessionId
            ));
        }

        this.dapServer = new DapServer(sessionId, serverConfig, workspace);
        // Note: setEventListener() must be called AFTER start() when dapClient is created
        // Listen to server status changes to update session state
        this.dapServer.addStatusChangeListener(this::onServerStatusChanged);
    }

    /**
     * Create a child debug session in response to a startDebugging request.
     * This is used when the debug adapter wants to debug a spawned process.
     * The child inherits the debugMode from the parent session.
     *
     * @param configuration the DAP configuration for the child session
     */
    private CompletableFuture<com.redhat.mcp.languagetools.dap.client.DapClient> createChildSession(
            Map<String, Object> configuration) {

        // Inherit debugMode from parent session
        boolean debugMode = this.debugMode;

        LOG.infof("Creating child debug session for parent session: %s (inherited debugMode=%s)", sessionId, debugMode);

        // Generate a child session ID
        // IMPORTANT: Like lsp4ij, child sessions REUSE the same DAP server process
        // Only ONE vscode-js-debug server handles parent + all children
        // Each child gets its own DapClient but shares the same IDebugProtocolServer

        String childSessionId = sessionId + "-child-" + (childSessions.size() + 1);
        LOG.infof("Creating child debug session: %s", childSessionId);

        // Create a new DapClient for the child (like lsp4ij)
        // Each child gets its own DapClient + Launcher, sharing the same transport streams
        DapClient childClient = new DapClient(this.dapServer.getDapClient());

        // Set event listener to forward child events to parent (or handle separately)
        childClient.setEventListener(new DapEventListener() {
            @Override
            public void onInitialized() {
                LOG.infof("Child session %s initialized", childSessionId);
            }

            @Override
            public void onStopped(StoppedEventArguments event) {
                LOG.infof("Child session %s stopped", childSessionId);
            }

            @Override
            public void onContinued(ContinuedEventArguments event) {
                LOG.infof("Child session %s continued", childSessionId);
            }

            @Override
            public void onTerminated(TerminatedEventArguments event) {
                LOG.infof("Child session %s terminated", childSessionId);
                // Remove child from parent's list
                DapSession.this.dapServer.getDapClient().getChildrenClients().remove(childClient);
            }

            @Override
            public void onThread(ThreadEventArguments event) {}

            @Override
            public void onOutput(OutputEventArguments event) {
                // Forward output to parent's trace collector
                String output = event.getOutput();
                LOG.infof("Child session %s onOutput called: category=%s, output=%s",
                    childSessionId, event.getCategory(), output);
                if (output != null && !output.isBlank()) {
                    String category = event.getCategory();

                    // Skip telemetry outputs
                    if ("telemetry".equals(category)) {
                        return;
                    }

                    com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection direction =
                        "stderr".equals(category)
                            ? com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT
                            : com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT;

                    // Use same color coding as parent: [Console], [Stdout], [Error]
                    // IMPORTANT: Use messageType to ensure outputs always show (not filtered by trace level)
                    String displayText;
                    TraceCollector.MessageType messageType;

                    if ("console".equals(category)) {
                        displayText = output.trim();  // Console output
                        messageType = TraceCollector.MessageType.INFO;
                    } else if ("stdout".equals(category)) {
                        displayText = output.trim();  // Standard output
                        messageType = TraceCollector.MessageType.INFO;
                    } else if ("stderr".equals(category)) {
                        displayText = output.trim();  // Error output
                        messageType = TraceCollector.MessageType.ERROR;
                    } else {
                        displayText = String.format("[Child DAP Output - %s] %s", category, output.trim());
                        messageType = TraceCollector.MessageType.TRACE;
                    }

                    // Use DapTraceCollector's addTrace with messageType
                    // IMPORTANT: Use parent sessionId so outputs appear in the same console
                    if (traceCollector instanceof DapTraceCollector) {
                        ((DapTraceCollector) traceCollector).addTrace(
                            workspace.getRootUri().toString(),
                            DapSession.this.sessionId,  // Use parent sessionId, not childSessionId
                            direction,
                            displayText,
                            messageType
                        );
                    } else {
                        traceCollector.addTrace(
                            workspace.getRootUri().toString(),
                            DapSession.this.sessionId,  // Use parent sessionId, not childSessionId
                            direction,
                            displayText
                        );
                    }
                }
            }

            @Override
            public void onBreakpoint(BreakpointEventArguments event) {}

            @Override
            public void onModule(ModuleEventArguments event) {}

            @Override
            public void onLoadedSource(LoadedSourceEventArguments event) {}

            @Override
            public void onProcess(ProcessEventArguments event) {}

            @Override
            public void onCapabilities(CapabilitiesEventArguments event) {}

            @Override
            public void onProgressStart(ProgressStartEventArguments event) {}

            @Override
            public void onProgressUpdate(ProgressUpdateEventArguments event) {}

            @Override
            public void onProgressEnd(ProgressEndEventArguments event) {}

            @Override
            public void onInvalidated(InvalidatedEventArguments event) {}

            @Override
            public void onMemory(MemoryEventArguments event) {}
        });

        // Create a NEW launcher for this child (like lsp4ij)
        // The launcher will share the same transport streams but route messages correctly
        IDebugProtocolServer childDebugServer = this.dapServer.createChildLauncher(childClient);

        // Initialize the child client with its own debug server proxy
        return childClient.connectAndInitialize(
                childDebugServer,  // Use the child's own debug server proxy
                configuration,
                debugMode,
                this.serverConfig.getServerId()
            )
            .thenApply(v -> childClient)
            .exceptionally(ex -> {
                LOG.errorf(ex, "Failed to create child debug session");
                throw new RuntimeException("Failed to create child debug session", ex);
            });
    }

    /**
     * Called when the DAP server status changes.
     * Propagates ERROR status to the session.
     * Note: DapSessionManager also listens to status changes and fires WebSocket events.
     */
    private void onServerStatusChanged(com.redhat.mcp.languagetools.server.ServerStatus oldStatus,
                                       com.redhat.mcp.languagetools.server.ServerStatus newStatus) {
        LOG.infof("DAP server status changed for session %s: %s -> %s", sessionId, oldStatus, newStatus);

        // Propagate ERROR status to session state
        if (newStatus == com.redhat.mcp.languagetools.server.ServerStatus.ERROR) {
            setState(SessionState.ERROR);
        }
    }

    // ========== Lifecycle ==========

    /**
     * Initialize the DAP server and establish connection.
     * Installation happens automatically inside dapServer.start().
     */
    public CompletableFuture<Void> initialize() {
        LOG.infof("Initializing DAP session: %s (%s)", sessionName, sessionId);
        return dapServer.start()
            .thenAccept(v -> {
                // Server is now RUNNING, session stays CREATED until launch
                LOG.infof("DAP session initialized: %s", sessionId);

                // Configure DapClient (must be done after start() because dapClient is created during start)
                if (dapServer.getDapClient() != null) {
                    // Set this session as the event listener
                    dapServer.getDapClient().setEventListener(DapSession.this);
                    // Set child session factory for startDebugging support
                    dapServer.getDapClient().setChildSessionFactory(this::createChildSession);
                }
            })
            .exceptionally(ex -> {
                LOG.errorf(ex, "Failed to initialize DAP session: %s", sessionId);
                setState(SessionState.ERROR);

                // Send error to traces
                traceCollector.addTrace(
                    workspace.getRootUri().toString(),
                    sessionId,
                    TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                    "❌ Failed to initialize: " + ex.getMessage()
                );

                // Propagate exception instead of returning null
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException("Failed to initialize DAP session", ex);
            });
    }

    /**
     * Launch a program for debugging.
     *
     * @param scriptPath Path to the script/program to debug
     * @param args Additional launch arguments
     */
    /**
     * Launch with full launch configuration (from launch.json).
     *
     * @param launchConfig the launch configuration
     * @param debugMode true to launch in debug mode (with breakpoints), false to run without debugging
     */
    public CompletableFuture<Map<String, Object>> launch(Map<String, Object> launchConfig, boolean debugMode) {
        LOG.infof("Launching debug session: %s (debugMode=%s) with config: %s", sessionId, debugMode, launchConfig);

        // Store debugMode for child sessions to inherit
        this.debugMode = debugMode;

        // Restart server if not running (first launch or after crash)
        CompletableFuture<Void> initFuture;
        var serverStatus = dapServer.getStatus();
        LOG.infof("Launch requested: sessionState=%s, serverStatus=%s", state, serverStatus);

        if (state == SessionState.CREATED
            || serverStatus == com.redhat.mcp.languagetools.server.ServerStatus.NOT_STARTED
            || serverStatus == com.redhat.mcp.languagetools.server.ServerStatus.START_FAILED
            || serverStatus == com.redhat.mcp.languagetools.server.ServerStatus.STOPPED) {
            LOG.infof("Server not running, starting and initializing...");
            initFuture = initialize();
        } else {
            LOG.infof("Server already running, skipping init");
            initFuture = CompletableFuture.completedFuture(null);
        }

        return initFuture.thenCompose(v -> {
            // Verify server is actually running
            if (dapServer.getStatus() != com.redhat.mcp.languagetools.server.ServerStatus.RUNNING) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                    "DAP server not running (status: " + dapServer.getStatus() + ")"));
            }

            // Resolve variables in launch config (like lsp4ij)
            Map<String, Object> resolvedConfig = resolveVariables(launchConfig);

            // Add workspace folder as cwd if not present
            if (!resolvedConfig.containsKey("cwd")) {
                String workspaceFolder = getWorkspaceFolderPath();
                resolvedConfig.put("cwd", workspaceFolder);
            }

            // For launch requests, add noDebug if running without debugging (like lsp4ij)
            String requestType = (String) resolvedConfig.get("request");
            if ("launch".equals(requestType) && !debugMode) {
                resolvedConfig.put("noDebug", true);
            }

            LOG.infof("Resolved launch config (debugMode=%s): %s", debugMode, resolvedConfig);

            // Use DapClient.connectAndInitialize (exactly like lsp4ij DAPClient.connectToServer)
            return dapServer.getDapClient().connectAndInitialize(
                    dapServer.getDebugServer(),
                    resolvedConfig,
                    debugMode,  // Pass debugMode to control breakpoint initialization
                    dapServer.getConfig().getServerId()
                )
                .thenApply(result -> {
                    setState(SessionState.RUNNING);
                    LOG.infof("Debug session fully initialized and running");
                    return Map.of(
                        "success", true,
                        "state", "running",
                        "message", "Debugging started"
                    );
                });
        });
    }

    /**
     * Attach to a running process.
     *
     * @param processId Process ID to attach to
     */
    public CompletableFuture<Map<String, Object>> attach(int processId) {
        LOG.infof("Attaching debug session: %s to process: %d", sessionId, processId);

        Map<String, Object> attachArgs = new HashMap<>();
        attachArgs.put("processId", processId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not initialized"));
        }

        return server.attach(attachArgs)
            .thenApply(v -> {
                setState(SessionState.RUNNING);
                return Map.of(
                    "success", true,
                    "state", "attached",
                    "message", "Attached to process " + processId
                );
            });
    }

    /**
     * Terminate the debug session.
     * Follows lsp4ij pattern: disconnect first, then stop server, handling errors gracefully.
     */
    public CompletableFuture<Void> terminate() {
        LOG.infof("Terminating DAP session: %s (with %d child sessions)", sessionId, childSessions.size());

        // First, terminate all child sessions
        List<CompletableFuture<Void>> childTerminations = new ArrayList<>();
        for (DapSession child : childSessions) {
            childTerminations.add(child.terminate());
        }

        return CompletableFuture.allOf(childTerminations.toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                // Then terminate this session
                IDebugProtocolServer server = dapServer.getDebugServer();
                if (server != null) {
                    DisconnectArguments args = new DisconnectArguments();
                    args.setTerminateDebuggee(true);

                    // Send disconnect, but handle errors gracefully (like lsp4ij)
                    // The server may have already closed the connection after 'terminated' event
                    return server.disconnect(args)
                        .handle((result, error) -> {
                            if (error != null) {
                                // Log but don't fail - socket might already be closed
                                LOG.debugf("Disconnect error (expected if server already terminated): %s",
                                    error.getMessage());
                                // Show in UI
                                traceCollector.addTrace(
                                    workspace.getRootUri().toString(),
                                    sessionId,
                                    com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                                    "Disconnect completed (server may have already terminated)"
                                );
                            }
                            return null;
                        })
                        .thenCompose(v2 -> dapServer.stop2())
                        .thenAccept(v2 -> {
                            setState(SessionState.TERMINATED);
                            childSessions.clear();
                            LOG.infof("DAP session terminated: %s", sessionId);
                        });
                }

                // No server, just stop
                return dapServer.stop2()
                    .thenAccept(v2 -> {
                        setState(SessionState.TERMINATED);
                        childSessions.clear();
                    });
            })
            .exceptionally(t -> {
                // Catch any remaining errors and show in UI
                LOG.errorf(t, "Error during session termination: %s", sessionId);
                traceCollector.addTrace(
                    workspace.getRootUri().toString(),
                    sessionId,
                    com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.CLIENT_TO_SERVER,
                    "⚠️ Termination error: " + t.getMessage()
                );

                // Still mark as terminated and clean up
                setState(SessionState.TERMINATED);
                childSessions.clear();

                // Force stop the server
                dapServer.stop2();

                return null;
            });
    }

    // ========== Breakpoints ==========

    /**
     * Set a breakpoint at a specific file and line.
     */
    public CompletableFuture<BreakpointInfo> setBreakpoint(String file, int line, String condition) {
        String breakpointId = UUID.randomUUID().toString();
        BreakpointInfo info = new BreakpointInfo(breakpointId, file, line, condition);
        breakpoints.put(breakpointId, info);

        LOG.infof("Setting breakpoint: %s at %s:%d", breakpointId, file, line);

        // Send to DAP server
        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.completedFuture(info);
        }

        SetBreakpointsArguments args = new SetBreakpointsArguments();
        Source source = new Source();
        source.setPath(file);
        args.setSource(source);

        SourceBreakpoint bp = new SourceBreakpoint();
        bp.setLine(line);
        if (condition != null && !condition.isEmpty()) {
            bp.setCondition(condition);
        }
        args.setBreakpoints(new SourceBreakpoint[]{bp});

        return server.setBreakpoints(args)
            .thenApply(response -> {
                if (response.getBreakpoints() != null && response.getBreakpoints().length > 0) {
                    Breakpoint dapBp = response.getBreakpoints()[0];
                    info.verified = dapBp.isVerified();
                    info.dapBreakpoint = dapBp;
                    LOG.infof("Breakpoint set and verified: %s", breakpointId);
                }
                return info;
            });
    }

    /**
     * Remove a breakpoint.
     */
    public CompletableFuture<Boolean> removeBreakpoint(String breakpointId) {
        BreakpointInfo info = breakpoints.remove(breakpointId);
        if (info == null) {
            return CompletableFuture.completedFuture(false);
        }

        LOG.infof("Removing breakpoint: %s", breakpointId);

        // Send updated breakpoint list to DAP server (without this breakpoint)
        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.completedFuture(true);
        }

        SetBreakpointsArguments args = new SetBreakpointsArguments();
        Source source = new Source();
        source.setPath(info.file);
        args.setSource(source);
        args.setBreakpoints(new SourceBreakpoint[0]); // Empty = remove all from this file

        return server.setBreakpoints(args)
            .thenApply(response -> true);
    }

    /**
     * List all breakpoints.
     */
    public List<BreakpointInfo> listBreakpoints() {
        return new ArrayList<>(breakpoints.values());
    }

    // ========== Execution Control ==========

    /**
     * Continue execution.
     */
    public CompletableFuture<Map<String, Object>> continueExecution() {
        LOG.infof("Continue execution: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No thread to continue"));
        }

        ContinueArguments args = new ContinueArguments();
        args.setThreadId(currentThreadId);

        return server.continue_(args)
            .thenApply(response -> {
                setState(SessionState.RUNNING);
                return Map.of(
                    "success", true,
                    "allThreadsContinued", response.getAllThreadsContinued() != null ? response.getAllThreadsContinued() : false
                );
            });
    }

    /**
     * Step over (next line).
     */
    public CompletableFuture<Void> stepOver() {
        LOG.infof("Step over: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No thread to step"));
        }

        NextArguments args = new NextArguments();
        args.setThreadId(currentThreadId);

        return server.next(args);
    }

    /**
     * Step into function.
     */
    public CompletableFuture<Void> stepIn() {
        LOG.infof("Step in: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No thread to step"));
        }

        StepInArguments args = new StepInArguments();
        args.setThreadId(currentThreadId);

        return server.stepIn(args);
    }

    /**
     * Step out of current function.
     */
    public CompletableFuture<Void> stepOut() {
        LOG.infof("Step out: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No thread to step"));
        }

        StepOutArguments args = new StepOutArguments();
        args.setThreadId(currentThreadId);

        return server.stepOut(args);
    }

    /**
     * Pause execution.
     */
    public CompletableFuture<Void> pause() {
        LOG.infof("Pause execution: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No thread to pause"));
        }

        PauseArguments args = new PauseArguments();
        args.setThreadId(currentThreadId);

        return server.pause(args)
            .thenAccept(v -> setState(SessionState.PAUSED));
    }

    // ========== Inspection ==========

    /**
     * Get stack trace for current thread.
     */
    public CompletableFuture<StackFrame[]> getStackTrace() {
        LOG.infof("Get stack trace: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null || currentThreadId == null) {
            return CompletableFuture.completedFuture(new StackFrame[0]);
        }

        StackTraceArguments args = new StackTraceArguments();
        args.setThreadId(currentThreadId);

        return server.stackTrace(args)
            .thenApply(response -> {
                currentStackFrames = response.getStackFrames();
                return currentStackFrames;
            });
    }

    /**
     * Get threads list.
     */
    public CompletableFuture<Thread[]> getThreads() {
        LOG.infof("Get threads: %s", sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.completedFuture(new Thread[0]);
        }

        return server.threads()
            .thenApply(response -> {
                threads = response.getThreads();
                if (threads.length > 0 && currentThreadId == null) {
                    currentThreadId = threads[0].getId();
                }
                return threads;
            });
    }

    /**
     * Get scopes for a stack frame.
     */
    public CompletableFuture<Scope[]> getScopes(int frameId) {
        LOG.infof("Get scopes for frame %d: %s", frameId, sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.completedFuture(new Scope[0]);
        }

        ScopesArguments args = new ScopesArguments();
        args.setFrameId(frameId);

        return server.scopes(args)
            .thenApply(ScopesResponse::getScopes);
    }

    /**
     * Get variables from a scope or variable reference.
     */
    public CompletableFuture<Variable[]> getVariables(int variablesReference) {
        LOG.infof("Get variables for reference %d: %s", variablesReference, sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.completedFuture(new Variable[0]);
        }

        VariablesArguments args = new VariablesArguments();
        args.setVariablesReference(variablesReference);

        return server.variables(args)
            .thenApply(VariablesResponse::getVariables);
    }

    /**
     * Evaluate an expression in the current debug context.
     */
    public CompletableFuture<EvaluateResponse> evaluate(String expression, Integer frameId) {
        LOG.infof("Evaluate expression '%s': %s", expression, sessionId);

        IDebugProtocolServer server = dapServer.getDebugServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("DAP server not initialized"));
        }

        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        if (frameId != null) {
            args.setFrameId(frameId);
        }
        args.setContext("watch");

        return server.evaluate(args);
    }

    // ========== Event Handlers (DapEventListener implementation) ==========

    @Override
    public void onInitialized() {
        LOG.infof("Session %s initialized event received", sessionId);
        // Server is now ready to receive configuration (breakpoints, etc.)
    }

    @Override
    public void onStopped(StoppedEventArguments event) {
        LOG.infof("Session %s stopped: %s (thread %d)", sessionId, event.getReason(), event.getThreadId());
        setState(SessionState.PAUSED);
        currentThreadId = event.getThreadId();
    }

    @Override
    public void onContinued(ContinuedEventArguments event) {
        LOG.infof("Session %s continued (thread %d)", sessionId, event.getThreadId());
        setState(SessionState.RUNNING);
    }

    @Override
    public void onTerminated(TerminatedEventArguments event) {
        LOG.infof("Session %s terminated", sessionId);
        setState(SessionState.TERMINATED);

        // Stop the DAP server when session terminates
        // This ensures a clean restart on next launch
        LOG.infof("Stopping DAP server after session termination: %s", sessionId);
        dapServer.stop2()
            .exceptionally(ex -> {
                LOG.warnf(ex, "Error stopping DAP server after termination: %s", ex.getMessage());
                return null;
            });
    }

    @Override
    public void onThread(ThreadEventArguments event) {
        LOG.infof("Session %s thread event: %s (thread %d)", sessionId, event.getReason(), event.getThreadId());
    }

    @Override
    public void onOutput(OutputEventArguments event) {
        String category = event.getCategory();
        String output = event.getOutput();

        LOG.debugf("Session %s output [%s]: %s", sessionId, category, output);

        // Send to trace collector for UI visibility
        if (output != null && !output.isBlank()) {
            // Skip telemetry outputs - they're just internal DAP metrics
            if ("telemetry".equals(category)) {
                return;
            }

            com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection direction =
                "stderr".equals(category)
                    ? TraceCollector.MessageDirection.SERVER_TO_CLIENT
                    : TraceCollector.MessageDirection.SERVER_TO_CLIENT;

            // For console/stdout/stderr, send just the output without trace prefix
            // These are ALWAYS shown, regardless of trace level
            String displayText;
            TraceCollector.MessageType messageType = TraceCollector.MessageType.INFO;

            if ("console".equals(category)) {
                displayText = output.trim();  // Console output (blue via messageType)
                messageType = TraceCollector.MessageType.INFO;
            } else if ("stdout".equals(category)) {
                displayText = output.trim();  // Standard output (cyan via messageType)
                messageType = TraceCollector.MessageType.INFO;
            } else if ("stderr".equals(category)) {
                displayText = output.trim();  // Error output (red via messageType)
                messageType = TraceCollector.MessageType.ERROR;
            } else {
                displayText = String.format("[DAP Output - %s] %s", category, output.trim());
                messageType = TraceCollector.MessageType.TRACE;
            }

            // Use the DapTraceCollector's addTrace with messageType
            if (traceCollector instanceof DapTraceCollector) {
                ((DapTraceCollector) traceCollector).addTrace(
                    workspace.getRootUri().toString(),
                    sessionId,
                    direction,
                    displayText,
                    messageType
                );
            } else {
                traceCollector.addTrace(
                    workspace.getRootUri().toString(),
                    sessionId,
                    direction,
                    displayText
                );
            }
        }
    }

    @Override
    public void onBreakpoint(BreakpointEventArguments event) {
        LOG.infof("Session %s breakpoint event: %s", sessionId, event.getReason());
        // Update breakpoint verification status if needed
        if (event.getBreakpoint() != null) {
            Breakpoint bp = event.getBreakpoint();
            // Find and update corresponding BreakpointInfo
            for (BreakpointInfo info : breakpoints.values()) {
                if (info.dapBreakpoint != null && info.dapBreakpoint.getId() != null
                    && info.dapBreakpoint.getId().equals(bp.getId())) {
                    info.verified = bp.isVerified();
                    info.dapBreakpoint = bp;
                    break;
                }
            }
        }
    }

    @Override
    public void onModule(ModuleEventArguments event) {
        LOG.debugf("Session %s module event: %s", sessionId, event.getReason());
    }

    @Override
    public void onLoadedSource(LoadedSourceEventArguments event) {
        LOG.debugf("Session %s loaded source: %s", sessionId, event.getReason());
    }

    @Override
    public void onProcess(ProcessEventArguments event) {
        LOG.infof("Session %s process event: %s", sessionId, event.getName());
    }

    @Override
    public void onCapabilities(CapabilitiesEventArguments event) {
        LOG.debugf("Session %s capabilities changed", sessionId);
    }

    @Override
    public void onProgressStart(ProgressStartEventArguments event) {
        LOG.debugf("Session %s progress start: %s", sessionId, event.getTitle());
    }

    @Override
    public void onProgressUpdate(ProgressUpdateEventArguments event) {
        LOG.debugf("Session %s progress update: %s", sessionId, event.getMessage());
    }

    @Override
    public void onProgressEnd(ProgressEndEventArguments event) {
        LOG.debugf("Session %s progress end", sessionId);
    }

    @Override
    public void onInvalidated(InvalidatedEventArguments event) {
        LOG.debugf("Session %s invalidated: %s", sessionId, event.getAreas());
        // Clear cached stack frames when invalidated
        currentStackFrames = new StackFrame[0];
    }

    @Override
    public void onMemory(MemoryEventArguments event) {
        LOG.debugf("Session %s memory changed: %s", sessionId, event.getMemoryReference());
    }

    // ========== Variable Resolution ==========

    /**
     * Resolve variables in DAP configuration (like lsp4ij).
     * Supports: ${workspaceFolder}, ${file}, ${fileBasename}, etc.
     */
    private Map<String, Object> resolveVariables(Map<String, Object> config) {
        Map<String, Object> resolved = new HashMap<>();
        String workspaceFolder = getWorkspaceFolderPath();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                // Resolve ${workspaceFolder}
                str = str.replace("${workspaceFolder}", workspaceFolder);
                // Resolve ${workspaceRoot} (deprecated alias)
                str = str.replace("${workspaceRoot}", workspaceFolder);
                resolved.put(entry.getKey(), str);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                List<Object> resolvedList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String str) {
                        str = str.replace("${workspaceFolder}", workspaceFolder);
                        str = str.replace("${workspaceRoot}", workspaceFolder);
                        resolvedList.add(str);
                    } else {
                        resolvedList.add(item);
                    }
                }
                resolved.put(entry.getKey(), resolvedList);
            } else {
                resolved.put(entry.getKey(), value);
            }
        }

        return resolved;
    }

    /**
     * Get workspace folder path as a proper file system path (not URI).
     * On Windows: "C:/path/to/workspace" (not "/C:/path/to/workspace")
     */
    private String getWorkspaceFolderPath() {
        String path = workspace.getRootUri().getPath();
        // On Windows, URI.getPath() returns "/C:/..." - remove leading slash
        if (path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return path;
    }

    // ========== Getters ==========

    public String getSessionId() {
        return sessionId;
    }

    public String getLanguage() {
        return language;
    }

    public String getSessionName() {
        return sessionName;
    }

    public SessionState getState() {
        return state;
    }

    /**
     * Set the callback to be called when session state changes.
     */
    public void setStateChangeCallback(Runnable callback) {
        this.stateChangeCallback = callback;
    }

    /**
     * Update session state and notify listeners.
     */
    private void setState(SessionState newState) {
        if (this.state != newState) {
            LOG.infof("Session %s state change: %s -> %s", sessionId, this.state, newState);
            this.state = newState;

            // Notify callback
            if (stateChangeCallback != null) {
                stateChangeCallback.run();
            }
        }
    }

    public DapServer getDapServer() {
        return dapServer;
    }

    public DapServerConfig getServerConfig() {
        return dapServer != null ? dapServer.getConfig() : null;
    }

    public Workspace getWorkspace() {
        return workspace;
    }
}
