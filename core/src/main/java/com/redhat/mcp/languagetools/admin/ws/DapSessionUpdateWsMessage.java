package com.redhat.mcp.languagetools.admin.ws;

public record DapSessionUpdateWsMessage(
    String type,  // "dap-session-update"
    String eventType,  // "CREATED", "STATE_CHANGED", "DELETED"
    String sessionId,
    String workspaceUri,
    String oldStatus,  // Server status (INSTALLING, STARTING, RUNNING, etc.)
    String newStatus,  // Server status (INSTALLING, STARTING, RUNNING, etc.)
    Boolean debugMode,  // true if debugging, false if running without debug
    String createdBy,
    String createdAt,  // ISO-8601 timestamp when session was created
    String launchBy,
    String launchedAt  // ISO-8601 timestamp when session was last launched
) {}
