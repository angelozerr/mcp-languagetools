/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.languagetools.admin;

import com.ibm.mcp.languagetools.admin.dto.McpClientDTO;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * REST API for MCP clients (AI connections).
 */
@Path("/api/admin/mcp/clients")
@Produces(MediaType.APPLICATION_JSON)
public class McpClientsResource {

    @Inject
    ConnectionManager connectionManager;

    /**
     * Get all connected MCP clients from ConnectionManager (with real connectionIds).
     */
    @GET
    public List<McpClientDTO> getClients() {
        return getCurrentClients();
    }

    /**
     * SSE stream REMOVED - use polling with GET /api/admin/clients instead.
     * SSE kept only for traces to avoid HTTP/1.1 connection pool exhaustion.
     * Event observers removed as they were only used for SSE broadcasting.
     */

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
                protocolVersion = initialRequest.protocolVersion().toString();
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
