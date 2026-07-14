package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.progress.AbstractProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress monitor that broadcasts progress updates via WebSocket to Admin UI.
 * Does NOT send trace messages (use TraceProgressMonitor for that).
 */
public class WebSocketProgressMonitor extends AbstractProgressMonitor {

    private final ProgressBroadcaster broadcaster;
    private final String taskId;
    private final String serverId;
    private final String title;
    private boolean stepsInitialized = false;

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

    /**
     * Override addStep to broadcast steps to WebSocket when all steps are defined.
     */
    @Override
    public ProgressMonitor addStep(String stepId, double weight) {
        return super.addStep(stepId, weight);
    }

    @Override
    public void initializeSteps() {
        if (stepsInitialized || broadcaster == null) {
            return;
        }

        List<com.redhat.mcp.languagetools.progress.ProgressBroadcaster.StepInfo> stepInfos = new ArrayList<>();
        for (var entry : getSteps().entrySet()) {
            var stepInfo = entry.getValue();
            stepInfos.add(new com.redhat.mcp.languagetools.progress.ProgressBroadcaster.StepInfo(
                stepInfo.getId(),
                stepInfo.getWeight(),
                stepInfo.getId()
            ));
        }

        if (!stepInfos.isEmpty()) {
            broadcaster.initTaskWithSteps(taskId, serverId, title, stepInfos, false);
            stepsInitialized = true;
        }
    }

    @Override
    public void reportProgress(double progress, String message) {
        double scaled = scaleToActiveStep(progress);
        setCurrent(scaled);
        if (broadcaster != null) {
            String stepId = getCurrentStepId();
            Double stepProgress = null;
            if (stepId != null) {
                double frac = getStepLocalFraction(scaled);
                if (frac >= 0) {
                    stepProgress = frac;
                }
            }
            broadcaster.taskRunning(taskId, serverId, title, scaled / total, message, stepId, stepProgress);
        }
    }

    @Override
    public void reportProgress(String message) {
        if (broadcaster != null) {
            String stepId = getCurrentStepId();
            Double stepProgress = null;
            if (stepId != null) {
                double frac = getStepLocalFraction(getCurrent());
                if (frac >= 0) {
                    stepProgress = frac;
                }
            }
            broadcaster.taskRunning(taskId, serverId, title, getCurrent() / total, message, stepId, stepProgress);
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
