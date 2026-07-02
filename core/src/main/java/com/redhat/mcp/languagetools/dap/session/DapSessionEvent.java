package com.redhat.mcp.languagetools.dap.session;

/**
 * CDI event fired when a DAP session is created, updated, or deleted.
 */
public class DapSessionEvent {

    public enum Type {
        CREATED,
        STATE_CHANGED,
        DELETED
    }

    private final Type type;
    private final String sessionId;
    private final String workspaceUri;
    private final String oldStatus;
    private final String newStatus;

    public DapSessionEvent(Type type, String sessionId, String workspaceUri) {
        this(type, sessionId, workspaceUri, null, null);
    }

    public DapSessionEvent(Type type, String sessionId, String workspaceUri, String oldStatus, String newStatus) {
        this.type = type;
        this.sessionId = sessionId;
        this.workspaceUri = workspaceUri;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public Type getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }
}
