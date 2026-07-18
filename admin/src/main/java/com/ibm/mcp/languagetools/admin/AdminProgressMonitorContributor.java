package com.ibm.mcp.languagetools.admin;

import com.ibm.mcp.languagetools.progress.ProgressBroadcaster;
import com.ibm.mcp.languagetools.progress.ProgressContext;
import com.ibm.mcp.languagetools.progress.ProgressMonitor;
import com.ibm.mcp.languagetools.progress.ProgressMonitorContributor;
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
        String taskId = context.getTaskId();
        String serverId = context.getServerId();
        String title = context.getTitle();

        LOG.infof("Creating WebSocketProgressMonitor for task '%s' (title=%s)", taskId, title);

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
