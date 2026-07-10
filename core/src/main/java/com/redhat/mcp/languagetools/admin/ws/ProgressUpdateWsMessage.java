package com.redhat.mcp.languagetools.admin.ws;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * WebSocket message for progress updates.
 */
@RegisterForReflection
public record ProgressUpdateWsMessage(
    String type,  // "progress-update"
    String taskId,  // Unique task ID (e.g., "install-eclipse-jdt-ls")
    String serverId,  // Server ID this task belongs to
    String title,  // Human-readable title (e.g., "Installing Eclipse JDT LS")
    Double progress,  // Progress fraction (0.0 to 1.0)
    String message,  // Optional progress message
    String status  // Task status: "running", "completed", "failed"
) {
}
