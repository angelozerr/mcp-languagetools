package com.ibm.mcp.languagetools.admin.ws;

public class ServerStatusChangedWsMessage extends WsMessage {

    private final String workspaceUri;
    private final String serverId;
    private final String oldStatus;
    private final String newStatus;
    private final String statusMessage;
    private final Double installProgress;
    private final Boolean isReady;

    public ServerStatusChangedWsMessage(String workspaceUri, String serverId,
                                        String oldStatus, String newStatus,
                                        String statusMessage, Double installProgress,
                                        Boolean isReady) {
        super(WsMessageType.SERVER_STATUS_CHANGED);
        this.workspaceUri = workspaceUri;
        this.serverId = serverId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.statusMessage = statusMessage;
        this.installProgress = installProgress;
        this.isReady = isReady;
    }

    public String getWorkspaceUri() { return workspaceUri; }
    public String getServerId() { return serverId; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getStatusMessage() { return statusMessage; }
    public Double getInstallProgress() { return installProgress; }
    public Boolean getIsReady() { return isReady; }
}
