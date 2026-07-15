package com.redhat.mcp.languagetools.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;

/**
 * REST endpoints for LSP trace messages.
 * Trace history and real-time updates are handled via WebSocket.
 */
@ApplicationScoped
@Path("/api/admin/lsp/traces")
public class LspTraceResource {

    @Inject
    LspTraceCollector traceCollector;

    @DELETE
    public void clearTraces() {
        traceCollector.clear();
    }
}
