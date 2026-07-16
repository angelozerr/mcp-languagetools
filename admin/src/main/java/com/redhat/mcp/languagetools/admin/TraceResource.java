package com.redhat.mcp.languagetools.admin;

import com.google.gson.JsonParser;
import com.redhat.mcp.languagetools.admin.ws.TraceLevelWsMessage;
import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.redhat.mcp.languagetools.settings.ServerTrace;
import com.redhat.mcp.languagetools.settings.Settings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/api/admin/traces")
@Produces(MediaType.APPLICATION_JSON)
public class TraceResource {

    @Inject
    LspTraceCollector lspTraceCollector;

    @Inject
    DapTraceCollector dapTraceCollector;

    @Inject
    McpTraceCollector mcpTraceCollector;

    @Inject
    Settings settings;

    @Inject
    Event<TraceLevelWsMessage> traceLevelEvent;

    // ========== Clear Traces ==========

    @DELETE
    @Path("/lsp")
    public void clearLspTraces() {
        lspTraceCollector.clear();
    }

    @DELETE
    @Path("/dap")
    public void clearDapTraces() {
        dapTraceCollector.clear();
    }

    @DELETE
    @Path("/mcp")
    public void clearMcpTraces() {
        mcpTraceCollector.clear();
    }

    // ========== LSP Trace Level ==========

    @PUT
    @Path("/lsp/{serverId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setLspTraceLevel(@PathParam("serverId") String serverId, String body) {
        try {
            ServerTrace level = parseTraceLevel(body);
            settings.setLspTraceLevel(serverId, level);
            traceLevelEvent.fire(new TraceLevelWsMessage("lsp", serverId, level.toString()));
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(400)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    // ========== DAP Trace Level ==========

    @PUT
    @Path("/dap/{serverId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setDapTraceLevel(@PathParam("serverId") String serverId, String body) {
        try {
            ServerTrace level = parseTraceLevel(body);
            settings.setDapTraceLevel(serverId, level);
            traceLevelEvent.fire(new TraceLevelWsMessage("dap", serverId, level.toString()));
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(400)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    // ========== MCP Trace Level ==========

    @PUT
    @Path("/mcp")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setMcpTraceLevel(String body) {
        try {
            ServerTrace level = parseTraceLevel(body);
            settings.setMcpTraceLevel(level);
            traceLevelEvent.fire(new TraceLevelWsMessage("mcp", null, level.toString()));
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(400)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    // ========== Helper ==========

    private ServerTrace parseTraceLevel(String body) {
        String traceLevel = JsonParser.parseString(body)
                .getAsJsonObject()
                .get("traceLevel")
                .getAsString();
        return ServerTrace.fromValue(traceLevel);
    }
}
