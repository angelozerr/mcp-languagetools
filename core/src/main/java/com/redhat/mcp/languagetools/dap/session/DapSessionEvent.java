package com.redhat.mcp.languagetools.dap.session;

import com.redhat.mcp.languagetools.server.ServerStatus;

import java.time.Instant;

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
    private final boolean debugMode;
    private final String createdBy;
    private final String createdAt;
    private final String launchedAt;
    private final String launchedBy;

    private DapSessionEvent(Type type,
                            String sessionId,
                            String workspaceUri,
                            String oldStatus,
                            String newStatus,
                            boolean debugMode,
                            DapSession.SessionActor createdBy,
                            Instant createdAt,
                            DapSession.SessionActor launchedBy,
                            Instant launchedAt) {
        this.type = type;
        this.sessionId = sessionId;
        this.workspaceUri = workspaceUri;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.debugMode = debugMode;
        this.createdBy = createdBy.name();
        this.createdAt = createdAt.toString();
        this.launchedBy = launchedBy.name();
        this.launchedAt = launchedAt != null ? launchedAt.toString() : null;
    }

    public static DapSessionEvent created(DapSession session) {
        return new DapSessionEvent(Type.CREATED,
                session.getSessionId(),
                session.getWorkspace().getNormalizedUri(),
                null,
                null,
                session.isDebugMode(),
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getLaunchedBy(),
                session.getLaunchedAt());
    }

    public static DapSessionEvent stateChanged(DapSession session,
                                               ServerStatus oldStatus,
                                               ServerStatus newStatus) {
        return new DapSessionEvent(Type.STATE_CHANGED, session.getSessionId(),
                session.getWorkspace().getNormalizedUri(),
                oldStatus != null ? oldStatus.name() : null,
                newStatus != null ? newStatus.name() : session.getState().name(),
                session.isDebugMode(),
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getLaunchedBy(),
                session.getLaunchedAt());
    }

    public static DapSessionEvent deleted(DapSession session) {
        return new DapSessionEvent(Type.DELETED,
                session.getSessionId(),
                session.getWorkspace().getNormalizedUri(),
                null,
                null,
                session.isDebugMode(),
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getLaunchedBy(),
                session.getLaunchedAt());
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

    public Boolean getDebugMode() {
        return debugMode;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLaunchedBy() {
        return launchedBy;
    }

    public String getLaunchedAt() {
        return launchedAt;
    }
}
