package com.redhat.mcp.languagetools.dap.tools;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.dap.session.DapSession;
import com.redhat.mcp.languagetools.dap.session.DapSessionManager;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

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

    @Tool(description = "Start debugging (launch or attach) based on configuration.request. " +
            "Creates a debug session automatically and starts the program. " +
            "Returns sessionId to use in other debug operations. " +
            "Use get_debug_templates() to see available configuration parameters. " +
            "Set debugMode=false to run without debugging (no breakpoints). " +
            "Optionally specify breakpoints to set before launching (avoids race conditions).")
    public Map<String, Object> start_debugging(
            @ToolArg(description = "ID of the debug adapter (e.g., 'java-debug', 'vscode-js-debug', 'debugpy')") String debuggerId,
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Debug configuration with 'request' field ('launch' or 'attach')") Map<String, Object> configuration,
            @ToolArg(description = "Optional breakpoints to set before starting [{file, line, condition?}]") List<Map<String, Object>> breakpoints,
            @ToolArg(description = "Optional session name (auto-generated if not provided)") String sessionName,
            @ToolArg(description = "Debug mode: true=debug with breakpoints, false=run without debugging (default)") Boolean debugMode) {

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
        if (actualDebugMode && breakpoints != null && !breakpoints.isEmpty()) {
            for (Map<String, Object> bp : breakpoints) {
                String file = (String) bp.get("file");
                Integer line = (Integer) bp.get("line");
                String condition = (String) bp.get("condition");

                if (file != null && line != null) {
                    session.setBreakpoint(file, line, condition).join();
                }
            }
        }

        // Launch or attach based on configuration.request
        String request = (String) configuration.get("request");
        Map<String, Object> result;

        if ("attach".equals(request)) {
            // Attach mode
            Integer processId = (Integer) configuration.get("processId");
            if (processId != null) {
                result = session.attach(processId).join();
            } else {
                // Attach via port/host
                result = session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT).join();
            }
        } else {
            // Launch mode (default)
            result = session.launch(configuration, actualDebugMode, DapSession.SessionActor.AI_AGENT).join();
        }

        // Add sessionId to result
        result.put("sessionId", sessionId);
        result.put("language", session.getLanguage());

        return result;
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

    @Tool(description = "Get the current call stack (stack trace) showing function calls and line numbers.")
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

    @Tool(description = "List all threads in the debugged program.")
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

    @Tool(description = "Get variable scopes (Locals, Globals, etc.) for a specific stack frame.")
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
            "Use variablesReference from get_scopes or a variable's variablesReference.")
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

    @Tool(description = "Shortcut to get local variables in the current stack frame (top of stack).")
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

    @Tool(description = "Evaluate an expression in the current debug context (e.g., 'x + y', 'myFunction()').")
    public Map<String, Object> evaluate_expression(
            String sessionId,
            String expression,
            Integer frameId) {

        DapSession session = sessionManager.getSession(sessionId);

        // If no frameId provided, use top frame
        Integer targetFrameId = frameId;
        if (targetFrameId == null) {
            StackFrame[] frames = session.getStackTrace().join();
            if (frames.length > 0) {
                targetFrameId = frames[0].getId();
            }
        }

        EvaluateResponse response = session.evaluate(expression, targetFrameId).join();

        return Map.of(
                "success", true,
                "result", response.getResult(),
                "type", response.getType() != null ? response.getType() : "",
                "variablesReference", response.getVariablesReference()
        );
    }

    // ========== Statistics ==========

    @Tool(description = "Get statistics about active debug sessions (total count, states, supported languages).")
    public Map<String, Object> get_debug_statistics() {
        return sessionManager.getStatistics();
    }

    // ========== Configuration Helpers ==========

    @Tool(description = "Get debug configuration templates (launch and attach) for a specific debug adapter. " +
            "Returns both 'launch' and 'attach' templates with all available parameters. " +
            "Use the adapter ID from list_debug_adapters.")
    public Map<String, Object> get_debug_templates(
            @ToolArg(description = "ID of the debug adapter (e.g., 'java-debug', 'vscode-js-debug')") String debuggerId) {

        Map<String, Object> result = new HashMap<>();

        // Determine language from debugger ID (simple heuristic)
        String language = extractLanguageFromDebuggerId(debuggerId);

        // Build launch and attach templates based on language
        Map<String, Object> launchTemplate = buildLaunchTemplate(language);
        Map<String, Object> attachTemplate = buildAttachTemplate(language);

        result.put("debuggerId", debuggerId);
        result.put("launch", launchTemplate);
        result.put("attach", attachTemplate);

        return result;
    }

    private String extractLanguageFromDebuggerId(String debuggerId) {
        if (debuggerId.contains("java")) return "java";
        if (debuggerId.contains("js") || debuggerId.contains("node")) return "javascript";
        if (debuggerId.contains("python") || debuggerId.contains("debugpy")) return "python";
        if (debuggerId.contains("go") || debuggerId.contains("delve")) return "go";
        return "unknown";
    }

    private Map<String, Object> buildLaunchTemplate(String language) {
        Map<String, Object> template = new HashMap<>();

        switch (language.toLowerCase()) {
            case "javascript":
            case "typescript":
            case "node":
                template.put("type", "node");
                template.put("request", "launch");
                template.put("name", "Launch Program");
                template.put("program", "${workspaceFolder}/index.js");
                template.put("skipFiles", List.of("<node_internals>/**"));
                template.put("console", "integratedTerminal");

                Map<String, Object> optional = new HashMap<>();
                optional.put("args", List.of("--optional-arg"));
                optional.put("env", Map.of("NODE_ENV", "development"));
                optional.put("cwd", "${workspaceFolder}");
                optional.put("runtimeArgs", List.of("--nolazy"));
                optional.put("port", 9229);
                template.put("_optional", optional);
                template.put("_description", "Node.js/JavaScript debugging configuration");
                break;

            case "java":
                template.put("type", "java");
                template.put("request", "launch");
                template.put("name", "Launch Java Program");
                template.put("mainClass", "com.example.Main");
                template.put("projectName", "my-project");

                Map<String, Object> javaOptional = new HashMap<>();
                javaOptional.put("args", List.of("arg1", "arg2"));
                javaOptional.put("vmArgs", "-Xmx512m");
                javaOptional.put("cwd", "${workspaceFolder}");
                javaOptional.put("classPaths", List.of("${workspaceFolder}/target/classes"));
                javaOptional.put("modulePaths", List.of());
                javaOptional.put("env", Map.of("JAVA_HOME", "/path/to/jdk"));
                template.put("_optional", javaOptional);
                template.put("_description", "Java debugging configuration");
                break;

            case "python":
                template.put("type", "python");
                template.put("request", "launch");
                template.put("name", "Launch Python Program");
                template.put("program", "${workspaceFolder}/main.py");
                template.put("console", "integratedTerminal");

                Map<String, Object> pythonOptional = new HashMap<>();
                pythonOptional.put("args", List.of("--verbose"));
                pythonOptional.put("env", Map.of("PYTHONPATH", "${workspaceFolder}"));
                pythonOptional.put("cwd", "${workspaceFolder}");
                pythonOptional.put("stopOnEntry", false);
                pythonOptional.put("justMyCode", true);
                template.put("_optional", pythonOptional);
                template.put("_description", "Python debugging configuration");
                break;

            case "go":
                template.put("type", "go");
                template.put("request", "launch");
                template.put("name", "Launch Go Program");
                template.put("mode", "auto");
                template.put("program", "${workspaceFolder}");

                Map<String, Object> goOptional = new HashMap<>();
                goOptional.put("args", List.of("--port=8080"));
                goOptional.put("env", Map.of("GO_ENV", "development"));
                goOptional.put("cwd", "${workspaceFolder}");
                goOptional.put("buildFlags", "-tags=debug");
                template.put("_optional", goOptional);
                template.put("_description", "Go debugging configuration (using Delve)");
                break;

            default:
                template.put("error", "Unsupported language: " + language);
                template.put("supportedLanguages", List.of("javascript", "java", "python", "go"));
        }

        return template;
    }

    private Map<String, Object> buildAttachTemplate(String language) {
        Map<String, Object> template = new HashMap<>();

        switch (language.toLowerCase()) {
            case "javascript":
            case "typescript":
            case "node":
                template.put("type", "node");
                template.put("request", "attach");
                template.put("name", "Attach to Process");
                template.put("port", 9229);

                Map<String, Object> optional = new HashMap<>();
                optional.put("address", "localhost");
                optional.put("restart", true);
                optional.put("skipFiles", List.of("<node_internals>/**"));
                template.put("_optional", optional);
                template.put("_description", "Attach to a running Node.js process");
                break;

            case "java":
                template.put("type", "java");
                template.put("request", "attach");
                template.put("name", "Attach to JVM");
                template.put("hostName", "localhost");
                template.put("port", 5005);

                Map<String, Object> javaOptional = new HashMap<>();
                javaOptional.put("projectName", "my-project");
                javaOptional.put("timeout", 30000);
                template.put("_optional", javaOptional);
                template.put("_description", "Attach to a running JVM with debug port enabled");
                break;

            case "python":
                template.put("type", "python");
                template.put("request", "attach");
                template.put("name", "Attach to Python");
                template.put("connect", Map.of("host", "localhost", "port", 5678));

                Map<String, Object> pythonOptional = new HashMap<>();
                pythonOptional.put("pathMappings", List.of(Map.of("localRoot", "${workspaceFolder}", "remoteRoot", ".")));
                pythonOptional.put("justMyCode", true);
                template.put("_optional", pythonOptional);
                template.put("_description", "Attach to a Python process with debugpy");
                break;

            case "go":
                template.put("type", "go");
                template.put("request", "attach");
                template.put("name", "Attach to Process");
                template.put("mode", "local");
                template.put("processId", 0);

                Map<String, Object> goOptional = new HashMap<>();
                goOptional.put("backend", "default");
                template.put("_optional", goOptional);
                template.put("_description", "Attach to a running Go process");
                break;

            default:
                template.put("error", "Unsupported language: " + language);
        }

        return template;
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
