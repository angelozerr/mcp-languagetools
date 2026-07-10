package com.redhat.mcp.languagetools.progress;

/**
 * SPI for contributing additional progress monitors.
 *
 * Modules can implement this interface to add their own progress monitors
 * (e.g., Admin module adds TraceProgressMonitor for WebSocket updates).
 *
 * Implementations are discovered via CDI and automatically included when
 * creating progress monitors via ProgressMonitorFactory.
 */
public interface ProgressMonitorContributor {

    /**
     * Create a progress monitor for the given context.
     *
     * @param context The progress context
     * @return A progress monitor, or null if not applicable for this context
     */
    ProgressMonitor createMonitor(ProgressContext context);

    /**
     * Priority for this contributor (higher = executed first).
     * Default is 0.
     */
    default int getPriority() {
        return 0;
    }
}
