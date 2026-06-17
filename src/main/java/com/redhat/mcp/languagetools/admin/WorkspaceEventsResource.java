package com.redhat.mcp.languagetools.admin;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;

/**
 * SSE endpoint for workspace change notifications.
 */
@Path("/api/admin/events")
public class WorkspaceEventsResource {

    @GET
    @Path("/workspaces")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> workspaceEvents() {
        // For now, just a heartbeat every 30s to keep connection alive
        // TODO: emit real events when workspace added/removed/updated
        return Multi.createFrom().ticks().every(Duration.ofSeconds(30))
                .map(tick -> "heartbeat");
    }
}
