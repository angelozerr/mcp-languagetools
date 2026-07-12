package com.redhat.mcp.languagetools.admin.ws;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * WebSocket message sent when a progress task is initialized with steps.
 * This allows the UI to prepare a multi-step progress bar.
 */
@RegisterForReflection
public record ProgressInitWsMessage(
    String type,  // "progress-init"
    String taskId,  // Unique task ID
    String serverId,  // Server ID (can be null for global tasks)
    String title,  // Task title
    List<StepInfo> steps,  // List of steps with their weights
    boolean cancellable  // Whether this task can be cancelled from Admin UI
) {

    /**
     * Information about a single step in the progress.
     */
    @RegisterForReflection
    public record StepInfo(
        String id,      // Step identifier (e.g., "install", "start", "indexing")
        double weight,  // Relative weight (e.g., 0.4 for 40%)
        String title    // Human-readable title
    ) {}
}
