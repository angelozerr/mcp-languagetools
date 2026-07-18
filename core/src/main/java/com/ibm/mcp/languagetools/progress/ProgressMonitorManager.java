package com.ibm.mcp.languagetools.progress;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ProgressMonitorManager {

    @Inject
    Instance<ProgressMonitorContributor> progressContributors;

    /**
     * Create a progress monitor that broadcasts to both MCP client and admin contributors.
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

        return monitors.size() > 1
                ? new MultiProgressMonitor(monitors.toArray(new ProgressMonitor[0]))
                : mcpMonitor;
    }
}
