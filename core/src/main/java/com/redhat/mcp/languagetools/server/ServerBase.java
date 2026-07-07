package com.redhat.mcp.languagetools.server;

import com.redhat.mcp.languagetools.client.BindEndpointSupport;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.trace.TracingMessageConsumer;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for server implementations (LSP and DAP).
 * Provides common functionality for managing server configuration and lifecycle,
 * as well as bindRequest/bindNotification support via BindEndpointSupport.
 *
 * @param <T> The type of server configuration (LspServerConfig or DapServerConfig)
 */
public abstract class ServerBase<T extends ServerConfigBase> extends BindEndpointSupport {

    private static final Logger LOG = Logger.getLogger(ServerBase.class);

    private final T config;
    private final Workspace workspace;
    protected ExecutorService executorService; // Not final - can be recreated after error
    private final TracingMessageConsumer.TraceCollectorAdd traceCollector;
    private final TracingMessageConsumer tracing;
    private volatile ServerStatus status = ServerStatus.NOT_STARTED;
    private volatile String statusMessage = null;
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>();

    protected Process serverProcess;
    private volatile boolean isReady;
    private CompletableFuture<Void> readyFuture;

    public ServerBase(T config, Workspace workspace) {
        this(config, workspace, config.getServerId());
    }

    /**
     * Constructor with explicit serverId for tracing.
     * Used by DAP to pass sessionId instead of serverId.
     */
    protected ServerBase(T config, Workspace workspace, String traceServerId) {
        super(config, workspace);
        this.config = config;
        this.workspace = workspace;
        this.executorService = Executors.newCachedThreadPool();
        this.readyFuture = new CompletableFuture<>();

        var workspaceRoot = workspace.getNormalizedUri();
        this.traceCollector = initializeTraceCollector(workspace);
        this.tracing = new TracingMessageConsumer(traceCollector, workspaceRoot, traceServerId);
    }

    /**
     * Get the server configuration.
     */
    public T getConfig() {
        return config;
    }

    public Path getServerHome() {
        return config.getServerHome();
    }
    /**
     * Get the current server status.
     */
    public final ServerStatus getStatus() {
        return status;
    }

    public String getId() {
        return config.getServerId();
    }

    /**
     * Functional interface for status change listener that receives both old and new status.
     */
    @FunctionalInterface
    public interface StatusChangeListener {
        void onStatusChanged(ServerStatus oldStatus, ServerStatus newStatus);
    }

    /**
     * Add a listener to be notified when server status changes.
     */
    public void addStatusChangeListener(StatusChangeListener listener) {
        if (listener != null) {
            statusChangeListeners.add(listener);
        }
    }

    /**
     * Remove a status change listener.
     */
    public void removeStatusChangeListener(StatusChangeListener listener) {
        statusChangeListeners.remove(listener);
    }

    /**
     * Update server status and notify all listeners.
     */
    public void setStatus(ServerStatus newStatus) {
        ServerStatus oldStatus = this.status;
        this.status = newStatus;

        // Clear status message when stopping/stopped
        if (newStatus == ServerStatus.STOPPING || newStatus == ServerStatus.STOPPED) {
            this.statusMessage = null;
        }

        LOG.infof("Server.setStatus: %s -> %s (listeners: %d)",
                oldStatus, newStatus, statusChangeListeners.size());

        if (oldStatus != newStatus && !statusChangeListeners.isEmpty()) {
            LOG.infof("Notifying %d listeners for %s: %s -> %s",
                    statusChangeListeners.size(), config.getServerId(), oldStatus, newStatus);
            for (StatusChangeListener listener : statusChangeListeners) {
                try {
                    listener.onStatusChanged(oldStatus, newStatus);
                } catch (Exception e) {
                    LOG.warnf(e, "Error in status change listener for %s", config.getServerId());
                }
            }
        }

        // Cleanup resources when entering terminal states
        if (newStatus == ServerStatus.ERROR
            || newStatus == ServerStatus.START_FAILED
            || newStatus == ServerStatus.STOPPED) {
            cleanupResources();
        }
    }

    /**
     * Cleanup resources (threads, processes) when server enters error state.
     * This is called synchronously when setStatus(ERROR) happens, so must be FAST.
     * Can be overridden by subclasses to add custom cleanup.
     */
    protected void cleanupResources() {
        LOG.infof("Cleaning up resources for %s (status: %s)", config.getServerId(), status);

        // Kill the server process if still running
        if (serverProcess != null) {
            if (serverProcess.isAlive()) {
                LOG.infof("Destroying server process (PID: %d)", serverProcess.pid());
                serverProcess.destroyForcibly();
            }
            serverProcess = null; // Always null it out
        }

        // DON'T shutdown executor - it would reject future start() attempts
        // Just let monitoring threads die naturally when streams close
        LOG.infof("Resources cleaned for %s (executor kept alive)", config.getServerId());
    }

    public final String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        String oldMessage = this.statusMessage;
        this.statusMessage = statusMessage;

        LOG.infof("[%s] setStatusMessage called: %s -> %s (listeners: %d)",
                config.getServerId(), oldMessage, statusMessage, statusChangeListeners.size());

        // Notify if message changed and listeners are registered
        if (!Objects.equals(oldMessage, statusMessage) && !statusChangeListeners.isEmpty()) {
            LOG.infof("[%s] Status message changed, notifying %d listeners", config.getServerId(), statusChangeListeners.size());
            // Trigger listeners to refresh UI
            for (StatusChangeListener listener : statusChangeListeners) {
                try {
                    listener.onStatusChanged(this.status, this.status);
                } catch (Exception e) {
                    LOG.warnf(e, "Error in status change listener for %s", config.getServerId());
                }
            }
        }
    }

    /**
     * Start monitoring stderr from the server process.
     * Captures errors and sends them to the trace collector.
     * Common implementation for both LSP and DAP servers.
     *
     * @param workspaceOrSessionId Workspace URI for LSP, sessionId for DAP
     * @param serverId Server ID for LSP, sessionId for DAP
     */
    protected void startStderrMonitoring(String workspaceOrSessionId, String serverId) {
        executorService.submit(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(serverProcess.getErrorStream()))) {
                String line;
                StringBuilder stackTraceBuffer = new StringBuilder();
                String stackTraceTimestamp = null;

                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    LOG.errorf("[%s stderr] %s", config.getServerId(), line);

                    String trimmed = line.trim();
                    boolean isStackTraceLine = trimmed.startsWith("at ") && trimmed.contains("(") && trimmed.contains(")");
                    boolean isExceptionLine = trimmed.contains("Exception:") || trimmed.contains("Error:");

                    if (isStackTraceLine || (isExceptionLine && stackTraceBuffer.isEmpty())) {
                        // Start or continue stack trace buffering
                        if (stackTraceBuffer.isEmpty()) {
                            stackTraceTimestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                        }
                        stackTraceBuffer.append(line).append("\n");
                    } else {
                        // Flush buffered stack trace if any
                        if (!stackTraceBuffer.isEmpty()) {
                            String errorTrace = String.format("[Error - %s] %s stderr: %s",
                                stackTraceTimestamp,
                                config.getName(),
                                stackTraceBuffer.toString().trim());
                            getTraceCollector().addTrace(
                                workspaceOrSessionId,
                                serverId,
                                com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                                errorTrace
                            );
                            stackTraceBuffer.setLength(0);
                            stackTraceTimestamp = null;
                        }

                        // Send current line as regular error
                        String errorTrace = String.format("[Error - %s] %s stderr: %s",
                            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                            config.getName(),
                            line);
                        getTraceCollector().addTrace(
                            workspaceOrSessionId,
                            serverId,
                            com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                            errorTrace
                        );
                    }
                }

                // Flush any remaining stack trace
                if (!stackTraceBuffer.isEmpty()) {
                    String errorTrace = String.format("[Error - %s] %s stderr: %s",
                        stackTraceTimestamp,
                        config.getName(),
                        stackTraceBuffer.toString().trim());
                    getTraceCollector().addTrace(
                        workspaceOrSessionId,
                        serverId,
                        com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                        errorTrace
                    );
                }

                LOG.infof("Stderr monitor for %s ended", config.getServerId());
            } catch (java.io.IOException e) {
                // Stream closed or interrupted - expected during shutdown
                if (!Thread.currentThread().isInterrupted()) {
                    LOG.errorf(e, "Error reading stderr for %s", config.getServerId());
                } else {
                    LOG.infof("Stderr monitor interrupted for %s", config.getServerId());
                }
            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error in stderr monitor for %s", config.getServerId());
            }
        });
    }

    /**
     * Wait until the server is ready, without timeout.
     * Returns a CompletableFuture that completes when the server is ready.
     * Uses notification mechanism (no polling) for efficiency.
     */
    public CompletableFuture<Void> waitUntilReady() {
        if (isReady) {
            return CompletableFuture.completedFuture(null);
        }
        return readyFuture;
    }

    /**
     * Wait until the server is ready, with a timeout.
     * Returns a CompletableFuture that completes when the server is ready.
     */
    public CompletableFuture<Void> waitUntilReady(long timeoutMs) {
        if (isReady) {
            return CompletableFuture.completedFuture(null);
        }

        return readyFuture.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Check if the language server is ready to handle requests.
     * For most servers, this is true after initialize() completes (status == RUNNING).
     * For servers like JDT.LS, this is determined by specific notifications (e.g., language/status).
     * Subclasses can override this method to provide custom readiness detection.
     */
    public final boolean isReady() {
        return isReady;
    }

    /**
     * Set the ready state of the language server.
     * Called by subclasses when they detect the server is ready (e.g., JdtLsServer on language/status).
     * Completes the readyFuture to notify all waiting threads.
     */
    public final void setReady(boolean ready) {
        boolean wasReady = this.isReady;
        this.isReady = ready;

        // Complete the future when becoming ready
        if (ready && !wasReady && readyFuture != null && !readyFuture.isDone()) {
            readyFuture.complete(null);
        }

        // Reset the future when becoming not ready (for restarts)
        if (!ready && wasReady) {
            readyFuture = new CompletableFuture<>();
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.infof("Stopping server: %s", config.getServerId());
            setStatus(ServerStatus.STOPPING);
            serverProcess.destroy();
            setStatus(ServerStatus.STOPPED);
        }
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    /**
     * Ensure server is installed before starting.
     * Should be called at the beginning of start() in subclasses.
     */
    protected CompletableFuture<Void> ensureInstalled() {
        return config.ensureInstalled(workspace.getApplication().getPathManager(), this::setStatus)
                .thenApply(result -> null);
    }

    /**
     * Check if server can start and prepare for starting.
     * Returns true if can proceed, false if should skip (already running).
     * Common logic for both LSP and DAP servers.
     */
    protected boolean checkAndPrepareStart() {
        // Don't restart if already running or starting
        if (getStatus() == ServerStatus.RUNNING || getStatus() == ServerStatus.STARTING) {
            LOG.warnf("Server already running/starting (status: %s), ignoring start call", getStatus());
            return false;
        }

        // If restarting, kill old process first
        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.infof("Killing old server process before restart (PID: %d)", serverProcess.pid());
            serverProcess.destroyForcibly();
        }

        // Set status to STARTING
        setStatus(ServerStatus.STARTING);
        return true;
    }

    /**
     * Log error with full stack trace to trace collector and update server status.
     */
    protected void logErrorToTrace(Exception e, com.redhat.mcp.languagetools.trace.TraceCollector traceCollector, String contextId) {
        LOG.errorf(e, "Failed to start %s", config.getServerId());

        // Build full stack trace
        StringBuilder stackTrace = new StringBuilder();
        stackTrace.append("[Error starting ").append(config.getName()).append("]\n");
        Throwable current = e;
        while (current != null) {
            stackTrace.append(current.getClass().getName()).append(": ").append(current.getMessage()).append("\n");
            for (StackTraceElement element : current.getStackTrace()) {
                stackTrace.append("  at ").append(element.toString()).append("\n");
            }
            current = current.getCause();
            if (current != null) {
                stackTrace.append("Caused by: ");
            }
        }

        // Send to trace collector
        try {
            traceCollector.addTrace(
                workspace.getNormalizedUri(),
                contextId,
                com.redhat.mcp.languagetools.trace.TraceCollector.MessageDirection.SERVER_TO_CLIENT,
                stackTrace.toString()
            );
        } catch (Exception traceEx) {
            LOG.errorf(traceEx, "Failed to add trace for error!");
        }

        // Update status
        setStatus(ServerStatus.ERROR);
        setStatusMessage(e.getMessage());
    }

    /**
     * Add error handler to a CompletableFuture that logs to trace collector.
     */
    protected <T> CompletableFuture<T> withErrorLogging(CompletableFuture<T> future,
                                                        com.redhat.mcp.languagetools.trace.TraceCollector traceCollector,
                                                        String contextId) {
        return future.exceptionally(throwable -> {
            Exception e = throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
            logErrorToTrace(e, traceCollector, contextId);
            return null;
        });
    }


    public TracingMessageConsumer.TraceCollectorAdd getTraceCollector() {
        return traceCollector;
    }

    public TracingMessageConsumer getTracing() {
        return tracing;
    }

    protected abstract TracingMessageConsumer.TraceCollectorAdd initializeTraceCollector(Workspace workspace);
}
