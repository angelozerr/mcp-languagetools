package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

/**
 * REST endpoints for MCP trace messages.
 * Trace history and real-time updates are handled via WebSocket.
 */
@ApplicationScoped
@Path("/api/admin/mcp/traces")
public class McpTracesResource {

    @Inject
    McpTraceCollector traceCollector;

    @DELETE
    public void clearTraces() {
        traceCollector.clearTraces();
    }
}
