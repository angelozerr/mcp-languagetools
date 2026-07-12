package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.admin.ws.ProgressInitWsMessage;
import com.redhat.mcp.languagetools.admin.ws.ProgressUpdateWsMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Broadcasts progress updates to the Admin UI via WebSocket.
 */
@ApplicationScoped
public class ProgressBroadcaster {

    private static final Logger LOG = Logger.getLogger(ProgressBroadcaster.class);

    @Inject
    Event<ProgressUpdateWsMessage> progressUpdateEvent;

    @Inject
    Event<ProgressInitWsMessage> progressInitEvent;

    /**
     * Send a progress update with step info.
     */
    public void sendProgress(String taskId, String serverId, String title,
                             double progress, String message, String status,
                             String stepId, Double stepProgress) {
        ProgressUpdateWsMessage msg = new ProgressUpdateWsMessage(
            "progress-update",
            taskId,
            serverId,
            title,
            progress,
            message,
            status,
            stepId,
            stepProgress
        );

        progressUpdateEvent.fire(msg);
    }

    /**
     * Send a progress update without step info.
     */
    public void sendProgress(String taskId, String serverId, String title,
                             double progress, String message, String status) {
        sendProgress(taskId, serverId, title, progress, message, status, null, null);
    }

    /**
     * Mark a task as running with step info.
     */
    public void taskRunning(String taskId, String serverId, String title,
                            double progress, String message,
                            String stepId, Double stepProgress) {
        sendProgress(taskId, serverId, title, progress, message, "running", stepId, stepProgress);
    }

    /**
     * Mark a task as running without step info.
     */
    public void taskRunning(String taskId, String serverId, String title, double progress, String message) {
        taskRunning(taskId, serverId, title, progress, message, null, null);
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

    /**
     * Initialize a task with steps.
     * This should be called before any progress updates to prepare the UI.
     *
     * @param taskId Unique task ID
     * @param serverId Server ID (can be null for global tasks)
     * @param title Task title
     * @param steps List of steps with their relative weights
     */
    public void initTaskWithSteps(String taskId, String serverId, String title,
                                  List<ProgressInitWsMessage.StepInfo> steps, boolean cancellable) {
        ProgressInitWsMessage msg = new ProgressInitWsMessage(
            "progress-init",
            taskId,
            serverId,
            title,
            steps,
            cancellable
        );

        progressInitEvent.fire(msg);
    }
}
