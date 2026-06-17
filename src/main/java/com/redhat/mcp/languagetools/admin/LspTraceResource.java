package com.redhat.mcp.languagetools.admin;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceMessage;

import java.util.List;

/**
 * REST endpoints for LSP trace messages.
 * Provides both historical trace retrieval and real-time SSE streaming.
 */
@ApplicationScoped
@Path("/api/admin/traces")
public class LspTraceResource {

    private static final Logger LOG = Logger.getLogger(LspTraceResource.class);

    @Inject
    LspTraceCollector traceCollector;

    @Inject
    io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor<LspTraceMessage> traceProcessor;

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

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<LspTraceMessage> streamTraces() {
        return Multi.createFrom().publisher(traceProcessor);
    }

    @DELETE
    public void clearTraces() {
        traceCollector.clear();
    }

    public void onTraceEvent(@Observes LspTraceMessage trace) {
        LOG.debugf("Broadcasting trace: %s", trace.serverId());
        traceProcessor.onNext(trace);
    }
}
