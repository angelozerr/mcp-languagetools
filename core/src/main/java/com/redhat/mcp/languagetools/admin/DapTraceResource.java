package com.redhat.mcp.languagetools.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.dap.trace.DapTraceMessage;

import java.util.List;

/**
 * REST endpoints for DAP trace messages.
 * Provides historical trace retrieval (real-time updates via WebSocket).
 */
@ApplicationScoped
@Path("/api/admin/dap/traces")
public class DapTraceResource {

    @Inject
    DapTraceCollector traceCollector;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<DapTraceMessage> getRecentTraces(@QueryParam("limit") @DefaultValue("100") int limit) {
        return traceCollector.getRecentTraces(limit);
    }

    @GET
    @Path("/session/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DapTraceMessage> getTracesForSession(
        @PathParam("sessionId") String sessionId,
        @QueryParam("limit") @DefaultValue("100") int limit
    ) {
        return traceCollector.getTracesForSession(sessionId, limit);
    }

    @DELETE
    public void clearTraces() {
        traceCollector.clear();
    }
}
