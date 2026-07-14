package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.admin.ws.ProgressInitWsMessage;
import com.redhat.mcp.languagetools.admin.ws.ProgressUpdateWsMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ProgressBroadcaster implements com.redhat.mcp.languagetools.progress.ProgressBroadcaster {

    private static final Logger LOG = Logger.getLogger(ProgressBroadcaster.class);

    @Inject
    Event<ProgressUpdateWsMessage> progressUpdateEvent;

    @Inject
    Event<ProgressInitWsMessage> progressInitEvent;

    @Override
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

    @Override
    public void sendProgress(String taskId, String serverId, String title,
                             double progress, String message, String status) {
        sendProgress(taskId, serverId, title, progress, message, status, null, null);
    }

    @Override
    public void taskRunning(String taskId, String serverId, String title,
                            double progress, String message,
                            String stepId, Double stepProgress) {
        sendProgress(taskId, serverId, title, progress, message, "running", stepId, stepProgress);
    }

    @Override
    public void taskRunning(String taskId, String serverId, String title, double progress, String message) {
        taskRunning(taskId, serverId, title, progress, message, null, null);
    }

    @Override
    public void taskCompleted(String taskId, String serverId, String title) {
        sendProgress(taskId, serverId, title, 1.0, null, "completed");
    }

    @Override
    public void taskFailed(String taskId, String serverId, String title, String errorMessage) {
        sendProgress(taskId, serverId, title, 0.0, errorMessage, "failed");
    }

    @Override
    public void initTaskWithSteps(String taskId, String serverId, String title,
                                  List<StepInfo> steps, boolean cancellable) {
        List<ProgressInitWsMessage.StepInfo> wsSteps = steps.stream()
                .map(s -> new ProgressInitWsMessage.StepInfo(s.id(), s.weight(), s.title()))
                .toList();

        ProgressInitWsMessage msg = new ProgressInitWsMessage(
            "progress-init",
            taskId,
            serverId,
            title,
            wsSteps,
            cancellable
        );

        progressInitEvent.fire(msg);
    }
}
