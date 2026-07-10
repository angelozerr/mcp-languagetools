package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.dap.session.DapSession;
import com.redhat.mcp.languagetools.installer.TraceProgressMonitor;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Helper class for creating ProgressMonitor instances in Admin UI endpoints.
 * Ensures consistent progress monitoring across all Admin operations.
 */
public class AdminProgressMonitorHelper {

    /**
     * Create a ProgressMonitor for an LSP server operation from Admin UI.
     * Returns TraceProgressMonitor with broadcasting if broadcaster available.
     *
     * @param server The LSP server (may be null if not yet created)
     * @param operation Operation name (e.g., "install", "start")
     * @return ProgressMonitor for the operation
     */
    public static ProgressMonitor forLspServer(LspServer server, String operation) {
        if (server == null) {
            return ProgressMonitor.none();
        }

        String taskId = operation + "-" + server.getId();
        String title = operation.substring(0, 1).toUpperCase() + operation.substring(1) + " " + server.getId();

        // Try to get ProgressBroadcaster from CDI
        ProgressBroadcaster broadcaster = null;
        try {
            broadcaster = CDI.current().select(ProgressBroadcaster.class).get();
        } catch (Exception e) {
            // CDI not available
        }

        return new TraceProgressMonitor(
            server.getTraceCollector(),
            100.0,
            broadcaster,
            taskId,
            server.getId(),
            title
        );
    }

    /**
     * Create a ProgressMonitor for an LSP server operation (default to "install").
     */
    public static ProgressMonitor forLspServer(LspServer server) {
        return forLspServer(server, "install");
    }

    /**
     * Create a ProgressMonitor for a DAP session operation from Admin UI.
     *
     * @param session The DAP session (may be null if not yet created)
     * @param operation Operation name (e.g., "launch")
     * @return ProgressMonitor for the operation
     */
    public static ProgressMonitor forDapSession(DapSession session, String operation) {
        if (session == null || session.getDapServer() == null) {
            return ProgressMonitor.none();
        }

        String taskId = operation + "-" + session.getSessionId();
        String title = operation.substring(0, 1).toUpperCase() + operation.substring(1) + " " + session.getSessionId();

        // Try to get ProgressBroadcaster from CDI
        ProgressBroadcaster broadcaster = null;
        try {
            broadcaster = CDI.current().select(ProgressBroadcaster.class).get();
        } catch (Exception e) {
            // CDI not available, will use trace-only
        }

        return new TraceProgressMonitor(
            session.getDapServer().getTraceCollector(),
            100.0,
            broadcaster,
            taskId,
            session.getSessionId(),
            title
        );
    }

    /**
     * Create a ProgressMonitor for a DAP session operation (default to "launch").
     */
    public static ProgressMonitor forDapSession(DapSession session) {
        return forDapSession(session, "launch");
    }
}
