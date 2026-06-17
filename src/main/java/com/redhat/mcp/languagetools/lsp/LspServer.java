package com.redhat.mcp.languagetools.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jboss.logging.Logger;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceMessage;
import com.redhat.mcp.languagetools.lsp.trace.TracingMessageConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generic Language Server instance.
 * Works with any LSP-compliant language server based on configuration.
 */
public class LspServer {

    private static final Logger LOG = Logger.getLogger(LspServer.class);

    private final LspServerConfig config;
    private final URI workspaceRoot;
    private final Path workspaceDataDir;
    private final Path serverHome;
    private final TracingMessageConsumer tracing;

    private Process serverProcess;
    private Socket socket;
    private LanguageServer languageServer;
    private final ExecutorService executorService;
    private final Map<String, List<Diagnostic>> diagnosticsCache = new ConcurrentHashMap<>();
    private final java.util.Set<String> openedFiles = ConcurrentHashMap.newKeySet();
    private volatile ServerStatus status = ServerStatus.STOPPED;
    private boolean isSocketConnection = false;
    private InstanceFileWatcher fileWatcher;
    private LspInstanceRegistry.InstanceInfo currentInstance;

    public LspServer(LspServerConfig config, URI workspaceRoot, Path workspaceDataDir, Path serverHome, LspTraceCollector traceCollector) {
        this.config = config;
        this.workspaceRoot = workspaceRoot;
        this.workspaceDataDir = workspaceDataDir;
        this.serverHome = serverHome;
        this.tracing = new TracingMessageConsumer(traceCollector, workspaceRoot.toString(), config.getId(), config.getName());
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Start the language server process and establish LSP communication.
     * First tries to connect to an existing instance via socket, falls back to launching a new process.
     */
    public CompletableFuture<Void> start() {
        status = ServerStatus.STARTING;
        return CompletableFuture.runAsync(() -> {
            try {
                // Try to find existing instance first
                String workspacePath = Paths.get(workspaceRoot).toString();
                LspInstanceRegistry.InstanceInfo existingInstance = LspInstanceRegistry.findInstance(workspacePath, config.getId());

                if (existingInstance != null) {
                    // Connect to existing instance via socket
                    status = ServerStatus.CONNECTING_TO_IDE;
                    try {
                        connectToSocket(existingInstance.port);
                        currentInstance = existingInstance;
                        LOG.infof("Connected to existing %s instance on port %d (PID: %d)",
                            config.getId(), existingInstance.port, existingInstance.pid);
                        startFileWatcher(workspacePath);
                        return;
                    } catch (IOException e) {
                        LOG.warnf("Failed to connect to existing instance on port %d, will launch new process: %s",
                            existingInstance.port, e.getMessage());
                        status = ServerStatus.STARTING;
                        // Fall through to launch new process
                    }
                }

                // No existing instance found or connection failed - launch new process
                launchProcess();
                startFileWatcher(workspacePath);

            } catch (IOException e) {
                LOG.errorf(e, "Failed to start %s", config.getId());
                throw new RuntimeException("Failed to start " + config.getId(), e);
            }
        }, executorService);
    }

    /**
     * Start a MCP-managed language server process only (do not connect to IDE instance).
     */
    public CompletableFuture<Void> startManagedOnly() {
        status = ServerStatus.STARTING;
        return CompletableFuture.runAsync(() -> {
            try {
                String workspacePath = Paths.get(workspaceRoot).toString();

                // Launch new process directly without checking for IDE instance
                launchProcess();
                startFileWatcher(workspacePath);

            } catch (IOException e) {
                LOG.errorf(e, "Failed to start %s", config.getId());
                throw new RuntimeException("Failed to start " + config.getId(), e);
            }
        }, executorService);
    }

    /**
     * Connect to an existing language server via socket.
     */
    private void connectToSocket(int port) throws IOException {
        LOG.infof("Connecting to %s on localhost:%d", config.getId(), port);

        socket = new Socket("localhost", port);
        isSocketConnection = true;

        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        // Create LSP client
        LanguageClient client = new GenericLanguageClient();

        // Create LSP launcher with message tracing wrapper
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client,
                in,
                out,
                executorService,
                consumer -> message -> {
                    // Log the message
                    tracing.log(message, consumer);
                    // Forward to original consumer
                    consumer.consume(message);
                }
        );

        languageServer = launcher.getRemoteProxy();
        launcher.startListening();

        LOG.infof("Socket connection established to %s on port %d", config.getId(), port);
    }

    /**
     * Launch a new language server process.
     */
    private void launchProcess() throws IOException {
        LOG.infof("Launching new %s process for workspace: %s", config.getId(), workspaceRoot);

        List<String> command = buildCommand();
        LOG.debugf("%s command: %s", config.getId(), String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);

        // Set environment variables
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            pb.environment().putAll(config.getEnv());
        }

        // Set working directory
        if (config.getWorkingDirectory() != null) {
            pb.directory(Paths.get(config.getWorkingDirectory()).toFile());
        }

        // Don't redirect error stream - we want to capture it separately
        serverProcess = pb.start();
        isSocketConnection = false;

        // Read and log stderr in background, send to trace collector
        executorService.submit(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(serverProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.errorf("[%s stderr] %s", config.getId(), line);

                    // Send to trace collector as error notification
                    String errorTrace = String.format("[Error - %s] %s stderr: %s",
                        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                        config.getName(),
                        line);
                    tracing.getCollector().addTrace(
                        workspaceRoot.toString(),
                        config.getId(),
                        config.getName(),
                        LspTraceMessage.MessageDirection.SERVER_TO_CLIENT,
                        errorTrace
                    );
                }
            } catch (Exception e) {
                LOG.debugf("Error stream reader closed: %s", e.getMessage());
            }
        });

        // Create LSP client
        LanguageClient client = new GenericLanguageClient();

        // Create LSP launcher with message tracing wrapper (like lsp4ij)
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client,
                serverProcess.getInputStream(),
                serverProcess.getOutputStream(),
                executorService,
                consumer -> message -> {
                    // Log the message
                    tracing.log(message, consumer);
                    // Forward to original consumer
                    consumer.consume(message);
                }
        );

        languageServer = launcher.getRemoteProxy();
        launcher.startListening();

        LOG.infof("%s process started for workspace: %s", config.getId(), workspaceRoot);
    }

    /**
     * Initialize the language server (send LSP initialize request).
     */
    public CompletableFuture<Void> initialize() {
        // If connected to external instance (IDE), server is already initialized
        if (isSocketConnection && currentInstance != null) {
            LOG.infof("%s already initialized by IDE (port %d, PID %d)",
                     config.getId(), currentInstance.port, currentInstance.pid);
            status = ServerStatus.CONNECTED_TO_IDE;
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("Initializing %s for workspace: %s", config.getId(), workspaceRoot);

        InitializeParams params = new InitializeParams();
        params.setRootUri(workspaceRoot.toString());

        // Set process ID
        params.setProcessId((int) ProcessHandle.current().pid());

        WorkspaceFolder workspaceFolder = new WorkspaceFolder();
        workspaceFolder.setUri(workspaceRoot.toString());
        workspaceFolder.setName(Paths.get(workspaceRoot).getFileName().toString());
        params.setWorkspaceFolders(List.of(workspaceFolder));

        // Client capabilities
        ClientCapabilities capabilities = new ClientCapabilities();

        WorkspaceClientCapabilities workspace = new WorkspaceClientCapabilities();
        workspace.setWorkspaceFolders(true);
        workspace.setConfiguration(true);
        capabilities.setWorkspace(workspace);

        TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
        textDocument.setPublishDiagnostics(new PublishDiagnosticsCapabilities());
        textDocument.setCodeAction(new CodeActionCapabilities());
        textDocument.setHover(new HoverCapabilities());
        textDocument.setDefinition(new DefinitionCapabilities());
        textDocument.setReferences(new ReferencesCapabilities());
        textDocument.setDocumentSymbol(new DocumentSymbolCapabilities());
        capabilities.setTextDocument(textDocument);

        params.setCapabilities(capabilities);

        // Set initialization options from config
        if (config.getInitializationOptions() != null && !config.getInitializationOptions().isEmpty()) {
            params.setInitializationOptions(config.getInitializationOptions());
        }

        return languageServer.initialize(params)
                .thenCompose(initResult -> {
                    LOG.infof("%s initialized for workspace: %s", config.getId(), workspaceRoot);
                    languageServer.initialized(new InitializedParams());
                    status = ServerStatus.RUNNING;
                    return CompletableFuture.completedFuture(null);
                });
    }

    /**
     * Shutdown the language server.
     */
    public CompletableFuture<Void> shutdown() {
        LOG.infof("Shutting down %s for workspace: %s", config.getId(), workspaceRoot);

        // Set status based on current connection type
        if (isSocketConnection && currentInstance != null) {
            status = ServerStatus.DISCONNECTING;
        } else {
            status = ServerStatus.STOPPING;
        }

        // Stop file watcher first
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }

        // Clear diagnostics cache
        diagnosticsCache.clear();

        if (languageServer == null && serverProcess == null && socket == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Try graceful shutdown first
                if (languageServer != null) {
                    try {
                        languageServer.shutdown().get(5, java.util.concurrent.TimeUnit.SECONDS);
                        languageServer.exit();
                    } catch (Exception e) {
                        LOG.warnf("Graceful shutdown failed for %s: %s", config.getId(), e.getMessage());
                    }
                }

                // Close socket connection if connected via socket
                if (isSocketConnection && socket != null) {
                    try {
                        socket.close();
                        LOG.infof("Closed socket connection to %s", config.getId());
                    } catch (IOException e) {
                        LOG.warnf("Failed to close socket: %s", e.getMessage());
                    }
                }

                // Force kill process if still alive (only if we launched it)
                if (!isSocketConnection && serverProcess != null && serverProcess.isAlive()) {
                    LOG.infof("Force killing %s process", config.getId());
                    serverProcess.destroyForcibly();

                    // Wait a bit for process to die
                    if (!serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        LOG.errorf("Failed to kill %s process", config.getId());
                    }
                }

                // Shutdown executor
                executorService.shutdown();
                if (!executorService.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }

                LOG.infof("%s shut down for workspace: %s", config.getId(), workspaceRoot);
                status = ServerStatus.STOPPED;

            } catch (Exception e) {
                LOG.errorf(e, "Error during shutdown of %s", config.getId());
                status = ServerStatus.STOPPED;
            }
        }, executorService);
    }

    /**
     * Build the command to launch the language server.
     * Supports variable substitution:
     * - ${workspace} → workspace root path
     * - ${workspaceDataDir} → workspace data directory
     * - ${serverHome} → language server installation directory
     * - ${configuration} → OS-specific config directory
     * - ${DATA_DIR} → workspace data directory
     * - ${user.name} → system user name
     */
    private List<String> buildCommand() throws IOException {
        String cmd = config.getCommandForCurrentOS();
        if (cmd == null) {
            throw new IOException("No command configured for current OS");
        }

        // Determine configuration directory based on OS
        String os = System.getProperty("os.name").toLowerCase();
        String configuration = serverHome.resolve(
            os.contains("win") ? "config_win" :
            os.contains("mac") ? "config_mac" : "config_linux"
        ).toString();

        // Substitute variables in command
        String resolved = cmd
                .replace("${workspace}", workspaceRoot.getPath())
                .replace("${workspaceDataDir}", workspaceDataDir.toString())
                .replace("${serverHome}", serverHome.toString())
                .replace("${configuration}", configuration)
                .replace("${DATA_DIR}", workspaceDataDir.toString())
                .replace("${user.name}", System.getProperty("user.name"));

        // Parse command string into arguments (simple split by spaces, respecting quotes)
        List<String> command = parseCommandLine(resolved);

        // Add any additional args from config
        for (String arg : config.getArgs()) {
            String resolvedArg = arg
                    .replace("${workspace}", workspaceRoot.getPath())
                    .replace("${workspaceDataDir}", workspaceDataDir.toString())
                    .replace("${serverHome}", serverHome.toString())
                    .replace("${configuration}", configuration)
                    .replace("${DATA_DIR}", workspaceDataDir.toString())
                    .replace("${user.name}", System.getProperty("user.name"));

            // Handle glob patterns (e.g., ${serverHome}/plugins/*.jar)
            resolvedArg = resolveGlob(resolvedArg);

            command.add(resolvedArg);
        }

        return command;
    }

    /**
     * Simple command line parser that respects quotes.
     */
    private List<String> parseCommandLine(String commandLine) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    /**
     * Resolve glob patterns in paths (e.g., /path/to/*.jar)
     */
    private String resolveGlob(String path) throws IOException {
        if (!path.contains("*")) {
            return path;
        }

        // Simple glob resolution: find first match
        int starIndex = path.indexOf('*');
        int lastSlash = path.lastIndexOf('/', starIndex);
        if (lastSlash == -1) {
            return path;
        }

        Path dir = Paths.get(path.substring(0, lastSlash));
        String pattern = path.substring(lastSlash + 1);

        if (!Files.exists(dir)) {
            return path;
        }

        // Find first matching file
        try (var stream = Files.list(dir)) {
            String globRegex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*");

            return stream
                    .filter(p -> p.getFileName().toString().matches(globRegex))
                    .findFirst()
                    .map(Path::toString)
                    .orElse(path);
        }
    }

    public LanguageServer getLanguageServer() {
        return languageServer;
    }

    public Map<String, List<Diagnostic>> getDiagnosticsCache() {
        return diagnosticsCache;
    }

    public LspServerConfig getConfig() {
        return config;
    }

    public ServerStatus getStatus() {
        return status;
    }

    public LspInstanceRegistry.InstanceInfo getCurrentInstance() {
        return currentInstance;
    }

    /**
     * Check if a file is currently opened in this server.
     */
    public boolean isFileOpened(String uri) {
        return openedFiles.contains(uri);
    }

    /**
     * Mark a file as opened.
     */
    public void markFileOpened(String uri) {
        openedFiles.add(uri);
    }

    /**
     * Mark a file as closed.
     */
    public void markFileClosed(String uri) {
        openedFiles.remove(uri);
    }

    /**
     * Start watching instance files for changes (IDE start/stop detection).
     */
    private void startFileWatcher(String workspacePath) {
        try {
            fileWatcher = new InstanceFileWatcher(
                workspacePath,
                config.getId(),
                this::handleInstanceChanged,
                this::handleInstanceRemoved
            );
            fileWatcher.start();
            LOG.infof("Started instance file watcher for %s", config.getId());
        } catch (IOException e) {
            LOG.warnf("Failed to start instance file watcher: %s", e.getMessage());
        }
    }

    /**
     * Handle when a new/updated instance is detected (e.g., IDE started).
     */
    private void handleInstanceChanged(LspInstanceRegistry.InstanceInfo newInstance) {
        // Only react if it's a different instance than our current one
        if (currentInstance != null && currentInstance.pid == newInstance.pid && currentInstance.port == newInstance.port) {
            LOG.debugf("Instance unchanged, ignoring");
            return;
        }

        LOG.infof("New instance detected (PID: %d, port: %d), switching connection...", newInstance.pid, newInstance.port);

        // If we launched our own server, stop it
        if (!isSocketConnection && serverProcess != null && serverProcess.isAlive()) {
            LOG.infof("Stopping our own server process to switch to IDE instance");
            try {
                languageServer.shutdown().get(2, java.util.concurrent.TimeUnit.SECONDS);
                languageServer.exit();
                serverProcess.destroyForcibly();
            } catch (Exception e) {
                LOG.warnf("Error stopping our server: %s", e.getMessage());
            }
        }

        // Close current socket if we're already connected
        if (isSocketConnection && socket != null) {
            try {
                socket.close();
                LOG.infof("Closed previous socket connection");
            } catch (IOException e) {
                LOG.warnf("Error closing socket: %s", e.getMessage());
            }
        }

        // Connect to new instance
        try {
            connectToSocket(newInstance.port);
            currentInstance = newInstance;

            // Re-initialize with new instance
            initialize().thenRun(() -> {
                LOG.infof("Successfully switched to new instance (PID: %d, port: %d)", newInstance.pid, newInstance.port);
            });
        } catch (IOException e) {
            LOG.errorf(e, "Failed to connect to new instance, will try to restart our own server");
            handleInstanceRemoved();
        }
    }

    /**
     * Handle when instance is removed (e.g., IDE closed).
     */
    private void handleInstanceRemoved() {
        // If we're connected via socket and the instance is gone, launch our own server
        if (isSocketConnection) {
            LOG.infof("External instance removed, launching our own server");

            // Close socket
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    LOG.debugf("Error closing socket: %s", e.getMessage());
                }
                socket = null;
            }

            currentInstance = null;
            isSocketConnection = false;
            languageServer = null;

            // Launch our own server
            try {
                launchProcess();
                initialize().thenRun(() -> {
                    LOG.infof("Successfully launched our own server after instance removal");
                });
            } catch (IOException e) {
                LOG.errorf(e, "Failed to launch server after instance removal");
                status = ServerStatus.STOPPED;
            }
        }
    }

    /**
     * Generic LSP client implementation.
     */
    private class GenericLanguageClient implements LanguageClient {

        @Override
        public void telemetryEvent(Object object) {
            // Ignore telemetry for now
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            LOG.debugf("Diagnostics published for: %s", diagnostics.getUri());
            diagnosticsCache.put(diagnostics.getUri(), diagnostics.getDiagnostics());
        }

        @Override
        public void showMessage(MessageParams messageParams) {
            LOG.infof("%s message: %s", config.getId(), messageParams.getMessage());
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
            LOG.infof("%s log: %s", config.getId(), message.getMessage());
        }
    }
}
