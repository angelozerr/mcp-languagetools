package com.ibm.mcp.languagetools.progress;

import java.util.List;

public interface ProgressBroadcaster {

    void sendProgress(String taskId, String serverId, String title,
                      double progress, String message, String status,
                      String stepId, Double stepProgress);

    void sendProgress(String taskId, String serverId, String title,
                      double progress, String message, String status);

    void taskRunning(String taskId, String serverId, String title,
                     double progress, String message, String stepId, Double stepProgress);

    void taskRunning(String taskId, String serverId, String title, double progress, String message);

    void taskCompleted(String taskId, String serverId, String title);

    void taskFailed(String taskId, String serverId, String title, String errorMessage);

    void initTaskWithSteps(String taskId, String serverId, String title,
                           List<StepInfo> steps, boolean cancellable);

    record StepInfo(String id, double weight, String title) {}
}
