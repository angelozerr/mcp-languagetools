package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.mcp.trace.McpTrace;
import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST API for MCP traffic traces.
 * Real-time updates via WebSocket.
 */
@Path("/api/admin/mcp/traces")
@Produces(MediaType.APPLICATION_JSON)
public class McpTracesResource {

    @Inject
    McpTraceCollector traceCollector;

    /**
     * Get all MCP traces.
     */
    @GET
    public List<McpTraceDto> getAllTraces(@QueryParam("limit") @DefaultValue("100") int limit) {
        return traceCollector.getTraces(limit).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Clear all MCP traces.
     */
    @DELETE
    public Response clearTraces() {
        traceCollector.clearTraces();
        return Response.ok().build();
    }

    private McpTraceDto toDto(McpTrace trace) {
        return new McpTraceDto(
                trace.direction(),
                trace.connectionId(),
                trace.message(),
                trace.timestamp().toString()
        );
    }

    @RegisterForReflection
    public record McpTraceDto(
            String direction,
            String connectionId,
            String jsonContent,
            String timestamp
    ) {
    }
}
