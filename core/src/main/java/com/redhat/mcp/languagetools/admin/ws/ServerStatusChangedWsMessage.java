package com.redhat.mcp.languagetools.admin.ws;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * WebSocket message for server status change events.
 */
@RegisterForReflection
public record ServerStatusChangedWsMessage(
    String type,  // "server-status-changed"
    String workspaceUri,
    String serverId,
    String oldStatus,
    String newStatus,
    String statusMessage,      // Status message (e.g., "Downloading dependencies...")
    Double installProgress,    // Installation progress (0.0 to 1.0), null if not installing
    Boolean isReady           // Whether server is ready to handle requests
) {
}
