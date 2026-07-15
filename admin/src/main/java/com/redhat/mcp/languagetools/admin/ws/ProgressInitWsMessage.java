package com.redhat.mcp.languagetools.admin.ws;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

public class ProgressInitWsMessage extends WsMessage {

    private final String taskId;
    private final String serverId;
    private final String title;
    private final List<StepInfo> steps;
    private final boolean cancellable;

    public ProgressInitWsMessage(String taskId, String serverId, String title,
                                 List<StepInfo> steps, boolean cancellable) {
        super(WsMessageType.PROGRESS_INIT);
        this.taskId = taskId;
        this.serverId = serverId;
        this.title = title;
        this.steps = steps;
        this.cancellable = cancellable;
    }

    public String getTaskId() { return taskId; }
    public String getServerId() { return serverId; }
    public String getTitle() { return title; }
    public List<StepInfo> getSteps() { return steps; }
    public boolean isCancellable() { return cancellable; }

    @RegisterForReflection
    public record StepInfo(String id, double weight, String title) {}
}
