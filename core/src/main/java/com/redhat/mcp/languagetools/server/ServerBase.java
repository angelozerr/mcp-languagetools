package com.redhat.mcp.languagetools.server;

import com.redhat.mcp.languagetools.lsp.RequestRouter;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
    private volatile ServerStatus status = ServerStatus.NOT_STARTED;
    private volatile String statusMessage = null;
    private Consumer<ServerStatus> statusChangeCallback;

    protected Process serverProcess;
    private volatile boolean isReady;

    public ServerBase(T config, Workspace workspace) {
        this.config = config;
        this.workspace = workspace;
        this.executorService = Executors.newCachedThreadPool();
        // Create RequestRouter for bindRequest routing
        this.requestRouter = createRequestRouter();
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
     * Functional interface for status change callback that receives both old and new status.
     */
    @FunctionalInterface
    public interface StatusChangeCallback {
        void onStatusChanged(ServerStatus oldStatus, ServerStatus newStatus);
    }

    /**
     * Set a callback to be notified when server status changes.
     * The callback receives both the old and new status.
     */
    public void setStatusChangeCallback(StatusChangeCallback callback) {
        this.statusChangeCallback = callback == null ? null : new Consumer<ServerStatus>() {
            private ServerStatus previousStatus = status;

            @Override
            public void accept(ServerStatus newStatus) {
                callback.onStatusChanged(previousStatus, newStatus);
                previousStatus = newStatus;
            }
        };
    }

    /**
     * Update server status and notify callback if registered.
     */
    public void setStatus(ServerStatus newStatus) {
        ServerStatus oldStatus = this.status;
        this.status = newStatus;

        // Clear status message when stopping/stopped
        if (newStatus == ServerStatus.STOPPING || newStatus == ServerStatus.STOPPED) {
            this.statusMessage = null;
        }

        LOG.infof("LspServer.setStatus: %s -> %s (callback registered: %s)",
                oldStatus, newStatus, statusChangeCallback != null);

        if (statusChangeCallback != null && oldStatus != newStatus) {
            LOG.infof("Calling status change callback for %s: %s -> %s", config.getServerId(), oldStatus, newStatus);
            try {
                statusChangeCallback.accept(newStatus);
            } catch (Exception e) {
                LOG.warnf(e, "Error in status change callback for %s", config.getServerId());
            }
        }
    }

    public final String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        String oldMessage = this.statusMessage;
        this.statusMessage = statusMessage;

        LOG.infof("[%s] setStatusMessage called: %s -> %s (callback: %s)",
                config.getServerId(), oldMessage, statusMessage, statusChangeCallback != null);

        // Notify if message changed and callback is registered
        if (statusChangeCallback != null && !java.util.Objects.equals(oldMessage, statusMessage)) {
            LOG.infof("[%s] Status message changed, firing callback", config.getServerId());
            // Trigger status change callback to refresh UI
            statusChangeCallback.accept(this.status);
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

}
