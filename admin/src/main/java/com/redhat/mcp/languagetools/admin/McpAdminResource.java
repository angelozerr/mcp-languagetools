package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.settings.Settings;
import com.redhat.mcp.languagetools.settings.ServerTrace;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoint for global configuration (MCP only).
 * LSP trace config is in LspAdminResource.
 */
@Path("/api/admin/mcp")
@Produces(MediaType.APPLICATION_JSON)
public class McpAdminResource {

    @Inject
    Settings settings;

    /**
     * Get MCP trace level.
     */
    @GET
    @Path("/config")
    public Response getMcpTraceLevel() {
        ServerTrace level = settings.getMcpTraceLevel();
        return Response.ok()
                .entity("{\"trace\": \"" + level + "\"}")
                .build();
    }

    /**
     * Set MCP trace level.
     */
    @PUT
    @Path("/config")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setMcpTraceLevel(String body) {
        try {
            String trace = com.google.gson.JsonParser.parseString(body)
                    .getAsJsonObject()
                    .get("trace")
                    .getAsString();

            ServerTrace level = ServerTrace.fromValue(trace);

            settings.setMcpTraceLevel(level);

            return Response.ok()
                    .entity("{\"trace\": \"" + level + "\"}")
                    .build();

        } catch (Exception e) {
            return Response.status(400)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}
