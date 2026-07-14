package com.redhat.mcp.languagetools.progress;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple ProgressMonitor instances across the application.
 * Allows tracking of concurrent operations:
 * - Multiple server installations
 * - Multiple server startings
 * - Multiple LSP operations (findReferences, etc.)
 */
@ApplicationScoped
public class ProgressMonitorManager {

    @Inject
    Instance<ProgressMonitorContributor> progressContributors;

    // Map: context ID -> ProgressMonitor
    private final Map<String, ProgressMonitor> monitors = new ConcurrentHashMap<>();

    // Map: context ID -> ProgressContext (for querying)
    private final Map<String, ProgressContext> contexts = new ConcurrentHashMap<>();

    /**
     * Register a new progress monitor.
     *
     * @param context The context identifying what is being tracked
     * @param monitor The progress monitor instance
     * @return The context ID (can be used to query progress later)
     */
    public String register(ProgressContext context, ProgressMonitor monitor) {
        String id = context.getId();
        contexts.put(id, context);
        monitors.put(id, monitor);
        return id;
    }

    /**
     * Get a progress monitor by context ID.
     */
    public ProgressMonitor getMonitor(String contextId) {
        return monitors.get(contextId);
    }

    /**
     * Get progress context by ID.
     */
    public ProgressContext getContext(String contextId) {
        return contexts.get(contextId);
    }

    /**
     * Get all active progress monitors.
     */
    public Collection<ProgressMonitor> getAllMonitors() {
        return monitors.values();
    }

    /**
     * Get all progress contexts.
     */
    public Collection<ProgressContext> getAllContexts() {
        return contexts.values();
    }

    /**
     * Get progress info for a specific context.
     */
    public ProgressInfo getProgressInfo(String contextId) {
        ProgressContext context = contexts.get(contextId);
        ProgressMonitor monitor = monitors.get(contextId);

        if (context == null || monitor == null) {
            return null;
        }

        return new ProgressInfo(context, monitor);
    }

    /**
     * Get all active progress info.
     */
    public Collection<ProgressInfo> getAllProgressInfo() {
        return contexts.keySet().stream()
                .map(this::getProgressInfo)
                .filter(info -> info != null)
                .toList();
    }

    /**
     * Remove a progress monitor (when operation completes).
     */
    public void unregister(String contextId) {
        contexts.remove(contextId);
        monitors.remove(contextId);
    }

    /**
     * Clear all monitors (for testing/shutdown).
     */
    public void clear() {
        contexts.clear();
        monitors.clear();
    }

    /**
     * Create a progress monitor that broadcasts to both MCP client and admin contributors.
     *
     * @param progress     MCP progress (from tool method)
     * @param cancellation MCP cancellation (from tool method)
     * @param context      Progress context identifying the operation
     * @return A progress monitor (MultiProgressMonitor if contributors exist, McpProgressMonitor otherwise)
     */
    public ProgressMonitor createProgressMonitor(Progress progress, Cancellation cancellation, ProgressContext context) {
        McpProgressMonitor mcpMonitor = new McpProgressMonitor(progress, cancellation);

        List<ProgressMonitor> monitors = new ArrayList<>();
        monitors.add(mcpMonitor);

        for (ProgressMonitorContributor contributor : progressContributors) {
            ProgressMonitor contributed = contributor.createMonitor(context);
            if (contributed != null && contributed != ProgressMonitor.none()) {
                monitors.add(contributed);
            }
        }

        ProgressMonitor result = monitors.size() > 1
                ? new MultiProgressMonitor(monitors.toArray(new ProgressMonitor[0]))
                : mcpMonitor;

        // Initialize steps for contributed monitors
        if (result instanceof MultiProgressMonitor multiMonitor) {
            for (ProgressMonitor monitor : multiMonitor.getDelegates()) {
                monitor.initializeSteps();
            }
        }

        return result;
    }

    /**
     * Combined progress information.
     */
    public static class ProgressInfo {
        private final ProgressContext context;
        private final ProgressMonitor monitor;

        public ProgressInfo(ProgressContext context, ProgressMonitor monitor) {
            this.context = context;
            this.monitor = monitor;
        }

        public ProgressContext getContext() {
            return context;
        }

        public ProgressMonitor getMonitor() {
            return monitor;
        }

        /**
         * Get progress fraction (0.0 - 1.0).
         */
        public double getFraction() {
            if (monitor instanceof AbstractProgressMonitor) {
                AbstractProgressMonitor apm = (AbstractProgressMonitor) monitor;
                return apm.getCurrent() / apm.getTotal();
            }
            return 0.0;
        }

        /**
         * Get progress percentage (0 - 100).
         */
        public int getPercentage() {
            return (int) Math.round(getFraction() * 100);
        }
    }
}
