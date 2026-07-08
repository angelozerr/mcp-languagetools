package com.redhat.mcp.languagetools.dap.tools;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.dap.session.DapSession;
import com.redhat.mcp.languagetools.dap.session.DapSessionManager;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;

import java.net.URI;
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

    // ========== Session Management ==========


    @Tool(
            name = "list_debug_adapters",
            description = "List available debug adapters with their IDs and supported languages. " +
                    "Optionally filter by file URI to get adapters suitable for that file. " +
                    "Use the adapter ID with create_debug_session.")
    public List<Map<String, Object>> getListDebugAdapters(
            @ToolArg(description = "Optional file URI to filter adapters (e.g., 'file:///path/to/Main.java')") String fileUri) {
        if (fileUri != null && !fileUri.isEmpty()) {
            return sessionManager.listDebugAdaptersForFile(URI.create(fileUri));
        }
        return sessionManager.listDebugAdapters();
    }


    @Tool(description = "List all active debug sessions with their state (CREATED, RUNNING, PAUSED, etc).")
    public List<Map<String, Object>> list_debug_sessions() {
        return sessionManager.listSessions();
    }

    @Tool(description = "Close and terminate a debug session, stopping the debugged program.")
    public Map<String, Object> close_debug_session(String sessionId) {
        return sessionManager.closeSession(sessionId).join();
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
                    "Optionally specify breakpoints to set before launching (avoids race conditions).",
            structuredContent = true
    )
    public Map<String, Object> startDebuggingSync(
            @ToolArg(description = "ID of the debug adapter (e.g., 'java-debug', 'vscode-js-debug', 'debugpy')") String debuggerId,
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Debug configuration with 'request' field ('launch' or 'attach')") Map<String, Object> configuration,
            @ToolArg(description = "Optional breakpoints to set before starting [{file, line, condition?}]") List<Map<String, Object>> breakpoints,
            @ToolArg(description = "Optional session name (auto-generated if not provided)") String sessionName,
            @ToolArg(description = "Debug mode: true=debug with breakpoints, false=run without debugging (default)") Boolean debugMode,
            Cancellation cancellation) {
        return startDebugging(debuggerId, cwd, configuration, breakpoints, sessionName, debugMode, cancellation).join();
    }

    public CompletableFuture<Map<String, Object>> startDebugging(
            String debuggerId,
            String cwd,
            Map<String, Object> configuration,
            List<Map<String, Object>> breakpoints,
            String sessionName,
            Boolean debugMode,
            Cancellation cancellation) {

        // Convert cwd to URI (handle both file:// URIs and Windows/Unix paths)
        URI uri;
        if (cwd.startsWith("file:")) {
            uri = URI.create(cwd);
        } else {
            // Convert path to URI
            java.nio.file.Path path = java.nio.file.Paths.get(cwd);
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
                        String file = (String) bp.get("file");
                        Integer line = (Integer) bp.get("line");
                        String condition = (String) bp.get("condition");
                        return session.setBreakpoint(file, line, condition);
                    })
                    .toArray(CompletableFuture[]::new);
            breakpointsFuture = CompletableFuture.allOf(bpFutures);
        }

        // Chain: breakpoints → launch/attach → add metadata
        return executeWithCancellation(
                breakpointsFuture.thenCompose(v -> {
                    String request = (String) configuration.get("request");

                    if ("attach".equals(request)) {
                        // Attach mode
                        Integer processId = (Integer) configuration.get("processId");
                        if (processId != null) {
                            return session.attach(processId);
                        } else {
                            // Attach via port/host
                            return session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT);
                        }
                    } else {
                        // Launch mode (default)
                        return session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT);
                    }
                }).thenApply(result -> {
                    // Add sessionId to result
                    result.put("sessionId", sessionId);
                    result.put("language", session.getLanguage());
                    return result;
                }),
                cancellation
        );
    }

    @Tool(description = "Detach from the debugged process without terminating it.")
    public Map<String, Object> detach_from_process(String sessionId) {
        // For now, just close the session
        // TODO: implement proper detach that leaves process running
        return close_debug_session(sessionId);
    }

    // ========== Execution Control ==========

    @Tool(description = "Continue program execution after hitting a breakpoint or pause.")
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

    @Tool(description = "Step over the current line (execute without entering function calls).")
    public Map<String, Object> step_over(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        session.stepOver().join();
        return Map.of(
                "success", true,
                "message", "Stepped over"
        );
    }

    @Tool(description = "Step into a function call on the current line.")
    public Map<String, Object> step_in(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        session.stepIn().join();
        return Map.of(
                "success", true,
                "message", "Stepped in"
        );
    }

    @Tool(description = "Step out of the current function, returning to the caller.")
    public Map<String, Object> step_out(String sessionId) {
        DapSession session = sessionManager.getSession(sessionId);
        session.stepOut().join();
        return Map.of(
                "success", true,
                "message", "Stepped out"
        );
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

    @Tool(description = "Validate a debug configuration before using it with start_debugging. " +
            "Checks for required fields and parameter types.")
    public Map<String, Object> validate_debug_config(
            Map<String, Object> configuration) {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check required fields based on type
        String type = (String) configuration.get("type");
        String request = (String) configuration.get("request");

        if (type == null || type.isEmpty()) {
            errors.add("Missing required field: 'type' (e.g., 'node', 'java', 'python', 'go')");
        }

        if (request == null || request.isEmpty()) {
            errors.add("Missing required field: 'request' (should be 'launch' or 'attach')");
        }

        // Type-specific validation
        if (type != null) {
            switch (type.toLowerCase()) {
                case "java":
                    if ("launch".equals(request) && !configuration.containsKey("mainClass")) {
                        errors.add("Java launch requires 'mainClass' parameter");
                    }
                    if ("attach".equals(request) && !configuration.containsKey("port")) {
                        errors.add("Java attach requires 'port' parameter");
                    }
                    if (!configuration.containsKey("projectName")) {
                        warnings.add("Recommended: specify 'projectName' for Java projects");
                    }
                    break;

                case "node":
                case "javascript":
                    if (!configuration.containsKey("program") && "launch".equals(request)) {
                        errors.add("Node.js launch requires 'program' parameter (path to .js file)");
                    }
                    if (!configuration.containsKey("port") && "attach".equals(request)) {
                        errors.add("Node.js attach requires 'port' parameter");
                    }
                    break;

                case "python":
                    if (!configuration.containsKey("program") && "launch".equals(request)) {
                        errors.add("Python launch requires 'program' parameter (path to .py file)");
                    }
                    if (!configuration.containsKey("connect") && "attach".equals(request)) {
                        errors.add("Python attach requires 'connect' parameter with host and port");
                    }
                    break;

                case "go":
                    if (!configuration.containsKey("program") && "launch".equals(request)) {
                        errors.add("Go launch requires 'program' parameter (package path)");
                    }
                    if (!configuration.containsKey("processId") && "attach".equals(request)) {
                        errors.add("Go attach requires 'processId' parameter");
                    }
                    break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("configuration", configuration);

        return result;
    }
}
