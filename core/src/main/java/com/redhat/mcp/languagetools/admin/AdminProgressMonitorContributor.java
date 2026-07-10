package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.progress.ProgressContext;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressMonitorContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Admin module's progress monitor contributor.
 * Adds TraceProgressMonitor for WebSocket broadcasting to Admin UI.
 */
@ApplicationScoped
public class AdminProgressMonitorContributor implements ProgressMonitorContributor {

    private static final Logger LOG = Logger.getLogger(AdminProgressMonitorContributor.class);

    @Inject
    ProgressBroadcaster broadcaster;

    @Override
    public ProgressMonitor createMonitor(ProgressContext context) {
        // Support both global progress (no serverId) and local progress (with serverId)
        String serverId = context.getServerId();  // null for global operations
        String operationName = context.getOperationName() != null ? context.getOperationName() : "operation";

        // Generate taskId and title based on whether this is global or local
        String taskId;
        String title;
        if (serverId != null) {
            // Local progress: "install-eclipse-jdtls", "start-eclipse-jdtls"
            taskId = operationName + "-" + serverId;
            title = operationName + " " + serverId;
        } else {
            // Global progress: "REFERENCES", "DEFINITION", etc.
            taskId = operationName;
            title = operationName;
        }

        LOG.infof("Creating WebSocketProgressMonitor for task '%s' (serverId=%s, type=%s)",
            taskId, serverId != null ? serverId : "global", context.getType());

        return new WebSocketProgressMonitor(
            broadcaster,
            taskId,
            serverId,  // Can be null for global operations
            title
        );
    }

    @Override
    public int getPriority() {
        // Lower priority than MCP monitor (which is 0)
        return -10;
    }
}
