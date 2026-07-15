package com.redhat.mcp.languagetools.admin.ws;

public class ProgressUpdateWsMessage extends WsMessage {

    private final String taskId;
    private final String serverId;
    private final String title;
    private final Double progress;
    private final String message;
    private final String status;
    private final String stepId;
    private final Double stepProgress;

    public ProgressUpdateWsMessage(String taskId, String serverId, String title,
                                   Double progress, String message, String status,
                                   String stepId, Double stepProgress) {
        super(WsMessageType.PROGRESS_UPDATE);
        this.taskId = taskId;
        this.serverId = serverId;
        this.title = title;
        this.progress = progress;
        this.message = message;
        this.status = status;
        this.stepId = stepId;
        this.stepProgress = stepProgress;
    }

    public String getTaskId() { return taskId; }
    public String getServerId() { return serverId; }
    public String getTitle() { return title; }
    public Double getProgress() { return progress; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getStepId() { return stepId; }
    public Double getStepProgress() { return stepProgress; }
}
