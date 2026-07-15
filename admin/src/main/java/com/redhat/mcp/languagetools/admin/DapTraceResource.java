package com.redhat.mcp.languagetools.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;

/**
 * REST endpoints for DAP trace messages.
 * Trace history and real-time updates are handled via WebSocket.
 */
@ApplicationScoped
@Path("/api/admin/dap/traces")
public class DapTraceResource {

    @Inject
    DapTraceCollector traceCollector;

    @DELETE
    public void clearTraces() {
        traceCollector.clear();
    }
}
