package com.redhat.mcp.languagetools.admin.ws;

public record DapSessionUpdateWsMessage(
    String type,  // "dap-session-update"
    String eventType,  // "CREATED", "STATE_CHANGED", "DELETED"
    String sessionId,
    String workspaceUri,
    String oldStatus,  // Server status (INSTALLING, STARTING, RUNNING, etc.)
    String newStatus   // Server status (INSTALLING, STARTING, RUNNING, etc.)
) {}
