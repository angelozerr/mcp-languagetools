package com.redhat.mcp.languagetools.server;

import com.redhat.mcp.languagetools.lsp.RequestRouter;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.trace.TracingMessageConsumer;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for server implementations (LSP and DAP).
 * Provides common functionality for managing server configuration and lifecycle.
 *
 * @param <T> The type of server configuration (LspServerConfig or DapServerConfig)
 */
public abstract class ServerBase<T extends ServerConfigBase> {

    private static final Logger LOG = Logger.getLogger(ServerBase.class);

    private final T config;
    private final Workspace workspace;
    protected final ExecutorService executorService;
    private final RequestRouter requestRouter;
    private final TracingMessageConsumer.TraceCollectorAdd traceCollector;
    private final TracingMessageConsumer tracing;
    private volatile ServerStatus status = ServerStatus.NOT_STARTED;
    private volatile String statusMessage = null;
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>();

    protected Process serverProcess;
    private volatile boolean isReady;

    public ServerBase(T config, Workspace workspace) {
        this(config, workspace, config.getServerId());
    }

    /**
     * Constructor with explicit serverId for tracing.
     * Used by DAP to pass sessionId instead of serverId.
     */
    protected ServerBase(T config, Workspace workspace, String traceServerId) {
        this.config = config;
        this.workspace = workspace;
        this.executorService = Executors.newCachedThreadPool();
        // Create RequestRouter for bindRequest routing
        this.requestRouter = createRequestRouter();

        var workspaceRoot = workspace.getRootUri();
        this.traceCollector = initializeTraceCollector(workspace);
        this.tracing = new TracingMessageConsumer(traceCollector, workspaceRoot.toString(), traceServerId);
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
        if (!java.util.Objects.equals(oldMessage, statusMessage) && !statusChangeListeners.isEmpty()) {
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
     * Wait until the server is ready, with a timeout.
     * Returns a CompletableFuture that completes when the server is ready.
     */
    public CompletableFuture<Void> waitUntilReady(long timeoutMs) {
        if (isReady) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            while (!isReady && (System.currentTimeMillis() - startTime) < timeoutMs) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for server to be ready", e);
                }
            }
            if (!isReady) {
                throw new RuntimeException("Server not ready after " + timeoutMs + "ms");
            }
        }, executorService);
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
     */
    public final void setReady(boolean ready) {
        this.isReady = ready;
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

    public RequestRouter getRequestRouter() {
        return requestRouter;
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
                workspace.getRootUri().toString(),
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

    /**
     * Create request router using the workspace to find servers.
     */
    private RequestRouter createRequestRouter() {
        return (targetServerId, method, params, mode) -> {
            // Look up the target server via workspace
            LspServer targetServer = getWorkspace().getLspServer(targetServerId);

            if (targetServer == null) {
                LOG.warnf("Target server '%s' not found for bindRequest: %s", targetServerId, method);
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Target server not found: " + targetServerId)
                );
            }

            // Wait for target server to be ready before routing (important for JDT.LS)
            LOG.debugf("Routing request %s to server %s (mode: %s), waiting for server to be ready...",
                    method, targetServerId, mode);

            return targetServer.waitUntilReady(30000) // 30 seconds timeout
                    .thenCompose(v -> {
                        LOG.debugf("Server %s is ready, routing request %s", targetServerId, method);

                        if ("direct".equals(mode)) {
                            // Direct JSON-RPC request
                            return targetServer.sendRequest(method, params);
                        } else {
                            // Default: workspace/executeCommand (for JDT.LS delegate handlers)
                            return targetServer.sendCommandRequest(method, params);
                        }
                    });
        };
    }

    public TracingMessageConsumer.TraceCollectorAdd getTraceCollector() {
        return traceCollector;
    }

    public TracingMessageConsumer getTracing() {
        return tracing;
    }

    protected abstract TracingMessageConsumer.TraceCollectorAdd initializeTraceCollector(Workspace workspace);
}
