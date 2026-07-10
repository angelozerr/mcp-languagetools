package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.progress.AbstractProgressMonitor;

/**
 * Progress monitor that broadcasts progress updates via WebSocket to Admin UI.
 * Does NOT send trace messages (use TraceProgressMonitor for that).
 */
public class WebSocketProgressMonitor extends AbstractProgressMonitor {

    private final ProgressBroadcaster broadcaster;
    private final String taskId;
    private final String serverId;
    private final String title;

    public WebSocketProgressMonitor(
            ProgressBroadcaster broadcaster,
            String taskId,
            String serverId,
            String title) {
        super(100.0);
        this.broadcaster = broadcaster;
        this.taskId = taskId;
        this.serverId = serverId;
        this.title = title;
    }

    @Override
    public void reportProgress(double progress, String message) {
        setCurrent(progress);
        if (broadcaster != null) {
            broadcaster.taskRunning(taskId, serverId, title, progress / total, message);
        }
    }

    @Override
    public void reportProgress(String message) {
        if (broadcaster != null) {
            broadcaster.taskRunning(taskId, serverId, title, getCurrent() / total, message);
        }
    }

    @Override
    public void setComplete() {
        setCurrent(total);
        if (broadcaster != null) {
            broadcaster.taskCompleted(taskId, serverId, title);
        }
    }

    @Override
    public boolean isSupported() {
        return broadcaster != null;
    }

    @Override
    public void checkCancelled() {
        // No cancellation support in WebSocket monitor
    }

    @Override
    public <T> java.util.concurrent.CompletableFuture<T> executeWithCancellation(
            java.util.concurrent.CompletableFuture<T> future) {
        // No cancellation support in WebSocket monitor - just pass through
        return future;
    }
}
