package com.redhat.mcp.languagetools.admin.ws;

public class DapSessionUpdateWsMessage extends WsMessage {

    private final String eventType;
    private final String sessionId;
    private final String workspaceUri;
    private final String oldStatus;
    private final String newStatus;
    private final Boolean debugMode;
    private final String createdBy;
    private final String createdAt;
    private final String launchBy;
    private final String launchedAt;

    public DapSessionUpdateWsMessage(String eventType, String sessionId,
                                      String workspaceUri, String oldStatus,
                                      String newStatus, Boolean debugMode,
                                      String createdBy, String createdAt,
                                      String launchBy, String launchedAt) {
        super(WsMessageType.DAP_SESSION_UPDATE);
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.workspaceUri = workspaceUri;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.debugMode = debugMode;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.launchBy = launchBy;
        this.launchedAt = launchedAt;
    }

    public String getEventType() { return eventType; }
    public String getSessionId() { return sessionId; }
    public String getWorkspaceUri() { return workspaceUri; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public Boolean getDebugMode() { return debugMode; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedAt() { return createdAt; }
    public String getLaunchBy() { return launchBy; }
    public String getLaunchedAt() { return launchedAt; }
}
