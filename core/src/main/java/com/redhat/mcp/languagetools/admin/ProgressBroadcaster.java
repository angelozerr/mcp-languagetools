package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.admin.ws.ProgressUpdateWsMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Broadcasts progress updates to the Admin UI via WebSocket.
 */
@ApplicationScoped
public class ProgressBroadcaster {

    private static final Logger LOG = Logger.getLogger(ProgressBroadcaster.class);

    @Inject
    Event<ProgressUpdateWsMessage> progressUpdateEvent;

    /**
     * Send a progress update.
     *
     * @param taskId Unique task ID (e.g., "install-eclipse-jdt-ls")
     * @param serverId Server ID this task belongs to
     * @param title Human-readable title
     * @param progress Progress fraction (0.0 to 1.0)
     * @param message Optional progress message
     * @param status Task status: "running", "completed", "failed"
     */
    public void sendProgress(String taskId, String serverId, String title, double progress, String message, String status) {
        ProgressUpdateWsMessage msg = new ProgressUpdateWsMessage(
            "progress-update",
            taskId,
            serverId,
            title,
            progress,
            message,
            status
        );

        progressUpdateEvent.fire(msg);
    }

    /**
     * Mark a task as running.
     */
    public void taskRunning(String taskId, String serverId, String title, double progress, String message) {
        sendProgress(taskId, serverId, title, progress, message, "running");
    }

    /**
     * Mark a task as completed.
     */
    public void taskCompleted(String taskId, String serverId, String title) {
        sendProgress(taskId, serverId, title, 1.0, null, "completed");
    }

    /**
     * Mark a task as failed.
     */
    public void taskFailed(String taskId, String serverId, String title, String errorMessage) {
        sendProgress(taskId, serverId, title, 0.0, errorMessage, "failed");
    }
}
