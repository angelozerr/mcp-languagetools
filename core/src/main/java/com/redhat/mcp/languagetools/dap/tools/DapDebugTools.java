package com.redhat.mcp.languagetools.dap.tools;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.dap.session.DapSession;
import com.redhat.mcp.languagetools.dap.session.DapSessionManager;
import com.redhat.mcp.languagetools.progress.ProgressContext;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressMonitorManager;
import com.redhat.mcp.languagetools.progress.ProgressStep;
import com.redhat.mcp.languagetools.utils.MapUtils;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.redhat.mcp.languagetools.tools.CancellationSupport.executeWithCancellation;

/**
 * MCP Tools for Debug Adapter Protocol (DAP) operations.
 * <p>
 * Provides 20+ tools to control debug sessions across multiple languages:
 * - Session management (create, list, close)
 * - Breakpoints (set, remove, list)
 * - Execution control (launch, attach, continue, step)
 * - Inspection (stack trace, variables, evaluate)
 */
@Singleton
public class DapDebugTools {

    @Inject
    DapSessionManager sessionManager;

    @Inject
    Application application;

    @Inject
    ProgressMonitorManager progressMonitorManager;

    // ========== Session Management ==========


    @Tool(
            name = "list_debug_adapters",
            description = "List available debug adapters with their IDs and supported languages. " +
                    "Optionally filter by file URI to get adapters suitable for that file. " +
                    "Without cwd: returns available debug adapter configurations. " +
                    "With cwd: returns debug adapter configurations enriched with installation status " +
                    "and error details to help diagnose adapter issues. " +
                    "Use the adapter ID with start_debugging.")
    public List<Map<String, Object>> listDebugAdapters(
            @ToolArg(description = "Optional file URI to filter adapters (e.g., 'file:///path/to/Main.java')") String fileUri,
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd) {
        List<Map<String, Object>> adapters;
        if (fileUri != null && !fileUri.isEmpty()) {
            adapters = sessionManager.listDebugAdaptersForFile(URI.create(fileUri));
        } else {
            adapters = sessionManager.listDebugAdapters();
        }

        if (cwd != null && !cwd.isEmpty()) {
            for (Map<String, Object> adapter : adapters) {
                String id = (String) adapter.get("id");
                var config = application.getDapServerConfig(id);
                if (config != null) {
                    config.addInstallationStatus(adapter);
                }
            }
        }

        return adapters;
    }

    @Tool(
            name = "list_debug_sessions",
            description = "List all active debug sessions with their state (CREATED, RUNNING, PAUSED, etc).")
    public List<Map<String, Object>> getListDebugSessions() {
        return sessionManager.listSessions();
    }

    @Tool(
            name = "close_debug_session",
            description = "Close and terminate a debug session, stopping the debugged program.")
    public Map<String, Object> closeDebugSessionSynch(String sessionId) {
        return closeDebugSession(sessionId)
                .join();
    }

    public CompletableFuture<Map<String, Object>> closeDebugSession(String sessionId) {
        return sessionManager.closeSession(sessionId);
    }

    // ========== Breakpoints ==========

    @Tool(description = "Set a breakpoint at a specific file and line number. " +
            "File path should be absolute or relative to workspace root. " +
            "Optionally add a condition (e.g., 'x > 10') to break only when true.")
    public Map<String, Object> set_breakpoint(
            String sessionId,
            String file,
            int line,
            String condition) {

        DapSession session = sessionManager.getSession(sessionId);
        DapSession.BreakpointInfo info = session.setBreakpoint(file, line, condition).join();

        return Map.of(
                "success", true,
                "breakpointId", info.breakpointId,
                "file", info.file,
                "line", info.line,
                "verified", info.verified,
                "message", "Breakpoint set at " + file + ":" + line
        );
    }

    @Tool(description = "Remove a previously set breakpoint by its ID.")
    public Map<String, Object> remove_breakpoint(
            String sessionId,
            String breakpointId) {

        DapSession session = sessionManager.getSession(sessionId);
        boolean removed = session.removeBreakpoint(breakpointId).join();

        return Map.of(
                "success", removed,
                "breakpointId", breakpointId,
                "message", removed ? "Breakpoint removed" : "Breakpoint not found"
        );
    }

    @Tool(description = "List all breakpoints currently set in a debug session.")
    public Map<String, Object> list_all_breakpoints(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        List<DapSession.BreakpointInfo> breakpoints = session.listBreakpoints();

        List<Map<String, Object>> bpList = breakpoints.stream()
                .map(bp -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("breakpointId", bp.breakpointId);
                    map.put("file", bp.file);
                    map.put("line", bp.line);
                    map.put("verified", bp.verified);
                    map.put("condition", bp.condition != null ? bp.condition : "");
                    return map;
                })
                .collect(Collectors.toList());

        return Map.of(
                "success", true,
                "count", bpList.size(),
                "breakpoints", bpList
        );
    }

    // ========== Debugging Lifecycle ==========

    @Tool(
            name = "start_debugging",
            description = "Start debugging (launch or attach) based on configuration.request. " +
                    "Creates a debug session automatically and starts the program. " +
                    "Returns sessionId to use in other debug operations. " +
                    "Use get_debug_templates() to see available configuration parameters. " +
                    "Set debugMode=false to run without debugging (no breakpoints). " +
                    "Optionally specify breakpoints to set before launching (avoids race conditions). " +
                    "After starting, use get_console_output(sessionId) to see program output (stdout/stderr/console.log).",
            structuredContent = true
    )
    public Map<String, Object> startDebuggingSync(
            @ToolArg(description = "ID of the debug adapter (e.g., 'java-debug', 'vscode-js-debug', 'debugpy')") String debuggerId,
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Debug configuration with 'request' field ('launch' or 'attach')") Map<String, Object> configuration,
            @ToolArg(description = "Optional breakpoints to set before starting [{file, line, condition?}]") List<Map<String, Object>> breakpoints,
            @ToolArg(description = "Optional session name (auto-generated if not provided)") String sessionName,
            @ToolArg(description = "Debug mode: true=debug with breakpoints, false=run without debugging (default)") Boolean debugMode,
            Cancellation cancellation,
            Progress progress) {
        return startDebugging(debuggerId, cwd, configuration, breakpoints, sessionName, debugMode, cancellation, progress)
                .join();
    }

    public CompletableFuture<Map<String, Object>> startDebugging(
            String debuggerId,
            String cwd,
            Map<String, Object> configuration,
            List<Map<String, Object>> breakpoints,
            String sessionName,
            Boolean debugMode,
            Cancellation cancellation,
            Progress progress) {

        // Create progress monitor (MCP + Admin WebSocket contributors)
        ProgressMonitor progressMonitor = progressMonitorManager.createProgressMonitor(
                progress, cancellation, ProgressContext.forOperation("start_debugging", "Start debugging"));

        // Define steps for DAP operations
        progressMonitor
                .addStep(ProgressStep.INSTALLING, 0.40)
                .addStep(ProgressStep.STARTING, 0.20)
                .addStep(ProgressStep.EXECUTING, 0.40);

        progressMonitor.beginStep(ProgressStep.INSTALLING);
        progressMonitor.reportProgress(0.0, "Installing debug adapter");

        // Convert cwd to URI (handle both file:// URIs and Windows/Unix paths)
        URI uri;
        if (cwd.startsWith("file:")) {
            uri = URI.create(cwd);
        } else {
            // Convert path to URI
            Path path = Paths.get(cwd);
            uri = path.toUri();
        }

        // Generate session name if not provided
        String actualSessionName = sessionName != null ? sessionName : "Debug Session";

        // Default debugMode to false if not provided (run without debugging)
        boolean actualDebugMode = debugMode != null ? debugMode : false;

        // Create session (created by AI agent via MCP)
        DapSession session = sessionManager.createSession(uri, debuggerId, actualSessionName, DapSession.SessionActor.AI_AGENT);
        String sessionId = session.getSessionId();

        // Set breakpoints before launching (if provided and in debug mode)
        CompletableFuture<Void> breakpointsFuture = CompletableFuture.completedFuture(null);
        if (actualDebugMode && breakpoints != null && !breakpoints.isEmpty()) {
            CompletableFuture<?>[] bpFutures = breakpoints.stream()
                    .filter(bp -> bp.get("file") != null && bp.get("line") != null)
                    .map(bp -> {
                        String file = MapUtils.getString(bp, "file");
                        int line = MapUtils.requireInteger(bp, "line");
                        String condition = MapUtils.getString(bp, "condition");
                        return session.setBreakpoint(file, line, condition);
                    })
                    .toArray(CompletableFuture[]::new);
            breakpointsFuture = CompletableFuture.allOf(bpFutures);
        }

        // Chain: breakpoints → launch/attach → add metadata
        // Note: progressMonitor.executeWithCancellation is already called inside trackFuture,
        // so we don't need to wrap again here (would create double wrapping)
        return breakpointsFuture.thenCompose(v -> {
            String request = MapUtils.getString(configuration, "request");

            if ("attach".equals(request)) {
                // Attach mode
                Integer processId = MapUtils.getInteger(configuration, "processId");
                if (processId != null) {
                    return session.attach(processId, progressMonitor);
                } else {
                    // Attach via port/host
                    return session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT, progressMonitor);
                }
            } else {
                // Launch mode (default)
                return session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT, progressMonitor);
            }
        }).thenApply(result -> {
            // Add sessionId to result
            result.put("sessionId", sessionId);
            result.put("language", session.getLanguage());

            // IMPORTANT: Check if session terminated during launch (e.g., program crashed immediately)
            // This can happen when the program has errors and exits before debugging starts
            if (session.getState() == DapSession.SessionState.TERMINATED) {
                // Override success flag - session started but immediately terminated
                result.put("success", false);
                result.put("state", "terminated");
                result.put("message", "Program terminated immediately after launch (check console output for errors)");
            }

            return result;
        }).whenComplete((result, ex) -> {
            progressMonitor.setComplete();
        }).exceptionally(ex -> {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("sessionId", sessionId);
            errorResult.put("error", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            return errorResult;
        });
    }

    @Tool(description = "Detach from the debugged process without terminating it.")
    public Map<String, Object> detach_from_process(String sessionId) {
        // For now, just close the session
        // TODO: implement proper detach that leaves process running
        return closeDebugSessionSynch(sessionId);
    }

    // ========== Execution Control ==========

    @Tool(description = "Continue program execution after hitting a breakpoint or pause. Returns console output (stdout/stderr) accumulated during execution.")
    public Map<String, Object> continue_execution(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        return session.continueExecution().join();
    }

    @Tool(description = "Pause the running program at the current line.")
    public Map<String, Object> pause_execution(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        session.pause().join();
        return Map.of(
                "success", true,
                "message", "Execution paused"
        );
    }

    @Tool(description = "Step over the current line (execute without entering function calls). Returns console output if any was printed during execution.")
    public Map<String, Object> step_over(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        return session.stepOver().join();
    }

    @Tool(description = "Step into a function call on the current line.")
    public Map<String, Object> step_in(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        return session.stepIn().join();
    }

    @Tool(description = "Step out of the current function, returning to the caller.")
    public Map<String, Object> step_out(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        return session.stepOut().join();
    }

    // ========== Inspection ==========

    @Tool(description = "Get the current call stack (stack trace) showing function calls and line numbers.", structuredContent = true)
    public Map<String, Object> get_stack_trace(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        StackFrame[] frames = session.getStackTrace().join();

        List<Map<String, Object>> framesList = Arrays.stream(frames)
                .map(frame -> {
                    Map<String, Object> frameMap = new HashMap<>();
                    frameMap.put("id", frame.getId());
                    frameMap.put("name", frame.getName());
                    frameMap.put("line", frame.getLine());
                    frameMap.put("column", frame.getColumn());
                    if (frame.getSource() != null) {
                        frameMap.put("file", frame.getSource().getPath());
                    }
                    return frameMap;
                })
                .collect(Collectors.toList());

        return Map.of(
                "success", true,
                "frames", framesList
        );
    }

    @Tool(description = "Get the console output (stdout/stderr/console.log) from the debugged program. Use this to see what the program has printed or logged. Very useful to understand program behavior without re-running. Returns up to 200 recent lines.")
    public Map<String, Object> get_console_output(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);

        String output = session.getProgramOutput().getAllWithCategories();
        int lineCount = session.getProgramOutput().getLineCount();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        if (lineCount > 0) {
            result.put("output", output);
            result.put("lines", lineCount);
        } else {
            result.put("output", "");
            result.put("lines", 0);
            result.put("message", "No console output yet");
        }

        return result;
    }

    @Tool(description = "List all threads in the debugged program.", structuredContent = true)
    public Map<String, Object> list_threads(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        Thread[] threads = session.getThreads().join();

        List<Map<String, Object>> threadsList = Arrays.stream(threads)
                .map(thread -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", thread.getId());
                    map.put("name", thread.getName());
                    return map;
                })
                .collect(Collectors.toList());

        return Map.of(
                "success", true,
                "threads", threadsList
        );
    }

    @Tool(description = "Get variable scopes (Locals, Globals, etc.) for a specific stack frame.", structuredContent = true)
    public Map<String, Object> get_scopes(
            String sessionId,
            int frameId) {

        DapSession session = sessionManager.getSession(sessionId);
        Scope[] scopes = session.getScopes(frameId).join();

        List<Map<String, Object>> scopesList = Arrays.stream(scopes)
                .map(scope -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", scope.getName());
                    map.put("variablesReference", scope.getVariablesReference());
                    map.put("expensive", scope.isExpensive());
                    return map;
                })
                .collect(Collectors.toList());

        return Map.of(
                "success", true,
                "scopes", scopesList
        );
    }

    @Tool(description = "Get variables from a scope or expandable variable. " +
            "Use variablesReference from get_scopes or a variable's variablesReference.",
            structuredContent = true)
    public Map<String, Object> get_variables(
            String sessionId,
            int variablesReference) {

        DapSession session = sessionManager.getSession(sessionId);
        Variable[] variables = session.getVariables(variablesReference).join();

        List<Map<String, Object>> varsList = Arrays.stream(variables)
                .map(var -> {
                    Map<String, Object> varMap = new HashMap<>();
                    varMap.put("name", var.getName());
                    varMap.put("value", var.getValue());
                    varMap.put("type", var.getType() != null ? var.getType() : "");
                    varMap.put("variablesReference", var.getVariablesReference());
                    varMap.put("expandable", var.getVariablesReference() > 0);
                    return varMap;
                })
                .collect(Collectors.toList());

        return Map.of(
                "success", true,
                "variables", varsList,
                "count", varsList.size()
        );
    }

    @Tool(description = "Shortcut to get local variables in the current stack frame (top of stack).", structuredContent = true)
    public Map<String, Object> get_local_variables(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);

        // Get top frame
        StackFrame[] frames = session.getStackTrace().join();
        if (frames.length == 0) {
            return Map.of(
                    "success", false,
                    "message", "No stack frames available"
            );
        }

        int frameId = frames[0].getId();

        // Get scopes for top frame
        Scope[] scopes = session.getScopes(frameId).join();

        // Find "Locals" scope
        Scope localsScope = Arrays.stream(scopes)
                .filter(s -> "Locals".equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElse(scopes.length > 0 ? scopes[0] : null);

        if (localsScope == null) {
            return Map.of(
                    "success", false,
                    "message", "No local scope found"
            );
        }

        // Get variables from locals scope
        return get_variables(sessionId, localsScope.getVariablesReference());
    }

    @Tool(
            name = "evaluate_expression",
            description = "Evaluate an expression in the current debug context (e.g., 'x + y', 'myFunction()').",
            structuredContent = true)
    public EvaluateResponse evaluateExpressionSync(
            String sessionId,
            String expression,
            Integer frameId,
            Cancellation cancellation) {
        return evaluateExpression(sessionId, expression, frameId, cancellation)
                .join();
    }

    public CompletableFuture<EvaluateResponse> evaluateExpression(
            String sessionId,
            String expression,
            Integer frameId,
            Cancellation cancellation) {

        DapSession session = sessionManager.getSession(sessionId);

        // If no frameId provided, use top frame
        if (frameId == null) {
            return executeWithCancellation(
                    session.getStackTrace()
                            .thenCompose(frames -> {
                                int targetFrameId = frames.length > 0 ? frames[0].getId() : 0;
                                return session.evaluate(expression, targetFrameId);
                            }),
                    cancellation
            );
        }

        return executeWithCancellation(session.evaluate(expression, frameId), cancellation);
    }

    // ========== Statistics ==========

    @Tool(description = "Get statistics about active debug sessions (total count, states, supported languages).")
    public Map<String, Object> get_debug_statistics() {
        return sessionManager.getStatistics();
    }

    // ========== Configuration Helpers ==========

    @Tool(
            name = "get_debug_templates",
            description = "Get debug configuration templates for a specific debug adapter. " +
                    "Returns templates grouped by type (launch, attach) from the debug adapter's configuration. " +
                    "Use the adapter ID from list_debug_adapters.",
            structuredContent = true
    )
    public DebugTemplatesResult getDebugTemplates(
            @ToolArg(description = "ID of the debug adapter (e.g., 'java-debug', 'vscode-js-debug')") String debuggerId) {

        // Get the real configuration templates from the debug adapter config
        var serverConfig = application.getDapServerConfig(debuggerId);
        if (serverConfig == null) {
            return new DebugTemplatesResult(
                    debuggerId,
                    List.of(),
                    List.of(),
                    "Unknown debug adapter: " + debuggerId + ". Use list_debug_adapters to see available adapters."
            );
        }

        var allTemplates = serverConfig.getConfigurationTemplates();

        // Group templates by type (launch vs attach)
        var launchTemplates = allTemplates.stream()
                .filter(t -> t.name.startsWith("launch."))
                .toList();

        var attachTemplates = allTemplates.stream()
                .filter(t -> t.name.startsWith("attach."))
                .toList();

        return new DebugTemplatesResult(debuggerId, launchTemplates, attachTemplates, null);
    }

    /**
     * Result class for debug templates, grouped by type.
     */
    public static class DebugTemplatesResult {
        public String debuggerId;
        public List<com.redhat.mcp.languagetools.dap.server.DapConfigurationTemplate> launch;
        public List<com.redhat.mcp.languagetools.dap.server.DapConfigurationTemplate> attach;
        public String error;

        public DebugTemplatesResult(
                String debuggerId,
                List<com.redhat.mcp.languagetools.dap.server.DapConfigurationTemplate> launch,
                List<com.redhat.mcp.languagetools.dap.server.DapConfigurationTemplate> attach,
                String error) {
            this.debuggerId = debuggerId;
            this.launch = launch;
            this.attach = attach;
            this.error = error;
        }
    }

}
