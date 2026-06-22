package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.admin.dto.McpClientDTO;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * REST API for MCP clients (AI connections).
 */
@Path("/api/admin/mcp-clients")
@Produces(MediaType.APPLICATION_JSON)
public class McpClientsResource {

    @Inject
    ConnectionManager connectionManager;

    private final List<io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor<List<McpClientDTO>>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Get all connected MCP clients from ConnectionManager (with real connectionIds).
     */
    @GET
    public List<McpClientDTO> getClients() {
        return getCurrentClients();
    }

    /**
     * SSE stream for real-time MCP client changes.
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<List<McpClientDTO>> streamClients() {
        var processor = io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor.<List<McpClientDTO>>create();
        subscribers.add(processor);

        // Send current state immediately
        processor.onNext(getCurrentClients());

        return processor
                .onCancellation().invoke(() -> subscribers.remove(processor))
                .onTermination().invoke(() -> subscribers.remove(processor));
    }

    /**
     * CDI observer to broadcast client changes to SSE subscribers.
     * Triggered by workspace/MCP client events.
     */
    void onMcpClientChange(@Observes McpClientChangeEvent event) {
        List<McpClientDTO> currentClients = getCurrentClients();
        subscribers.forEach(processor -> processor.onNext(currentClients));
    }

    /**
     * Get all MCP clients from ConnectionManager (with real connectionIds for traces).
     */
    private List<McpClientDTO> getCurrentClients() {
        List<McpClientDTO> clients = new ArrayList<>();

        for (McpConnectionBase connection : connectionManager) {
            var initialRequest = connection.initialRequest();

            String name = "Unknown";
            String version = null;
            String protocolVersion = null;

            if (initialRequest != null) {
                if (initialRequest.implementation() != null) {
                    name = initialRequest.implementation().name();
                    version = initialRequest.implementation().version();
                }
                protocolVersion = initialRequest.protocolVersion();
            }

            // Use connection.id() as ID so it matches MCP trace connectionId
            clients.add(new McpClientDTO(
                    connection.id(),          // connectionId for matching traces
                    name,                     // name
                    version,                  // version
                    protocolVersion,          // protocolVersion
                    null                      // connectedAt - not persisted
            ));
        }

        return clients;
    }
}
