package com.redhat.mcp.languagetools.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceMessage;

import java.util.List;

/**
 * REST endpoints for LSP trace messages.
 * Provides historical trace retrieval (real-time updates via WebSocket).
 */
@ApplicationScoped
@Path("/api/admin/lsp/traces")
public class LspTraceResource {

    @Inject
    LspTraceCollector traceCollector;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<LspTraceMessage> getRecentTraces(@QueryParam("limit") @DefaultValue("100") int limit) {
        return traceCollector.getRecentTraces(limit);
    }

    @GET
    @Path("/server/{serverId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<LspTraceMessage> getTracesForServer(
        @PathParam("serverId") String serverId,
        @QueryParam("limit") @DefaultValue("100") int limit
    ) {
        return traceCollector.getTracesForServer(serverId, limit);
    }

    @GET
    @Path("/workspace/{workspaceUri}/server/{serverId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<LspTraceMessage> getTracesForWorkspaceAndServer(
        @PathParam("workspaceUri") String workspaceUri,
        @PathParam("serverId") String serverId,
        @QueryParam("limit") @DefaultValue("100") int limit
    ) {
        return traceCollector.getTracesForWorkspaceAndServer(workspaceUri, serverId, limit);
    }

    @DELETE
    public void clearTraces() {
        traceCollector.clear();
    }
}
