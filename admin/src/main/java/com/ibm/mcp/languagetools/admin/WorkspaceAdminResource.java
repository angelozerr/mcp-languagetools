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

import com.ibm.mcp.languagetools.Application;
import com.ibm.mcp.languagetools.admin.dto.ContributionDTOBuilder;
import com.ibm.mcp.languagetools.admin.dto.LspServerDTO;
import com.ibm.mcp.languagetools.admin.dto.ServerDTOBuilder;
import com.ibm.mcp.languagetools.admin.dto.WorkspaceDTO;
import com.ibm.mcp.languagetools.dap.session.DapSessionManager;
import com.ibm.mcp.languagetools.workspace.Workspace;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceAdminResource {

    private static final Logger LOG = Logger.getLogger(WorkspaceAdminResource.class);

    @Inject
    Application application;

    @Inject
    ServerDTOBuilder serverDTOBuilder;

    @Inject
    DapSessionManager dapSessionManager;

    @Inject
    ContributionDTOBuilder contributionBuilder;

    @GET
    @Path("/workspaces")
    public List<WorkspaceDTO> listWorkspaces() {
        return getCurrentWorkspaces();
    }

    /**
     * SSE stream REMOVED - use polling with GET /api/admin/workspaces instead.
     * SSE kept only for traces to avoid HTTP/1.1 connection pool exhaustion.
     * Event observers removed as they were only used for SSE broadcasting.
     */

    private List<WorkspaceDTO> getCurrentWorkspaces() {
        return application.getWorkspaces()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @GET
    @Path("/workspaces/{uri}")
    public WorkspaceDTO getWorkspace(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);
        Workspace workspace = application.getWorkspace(uri);
        if (workspace == null) {
            throw new NotFoundException("Workspace not found: " + uri);
        }
        return toDTO(workspace);
    }

    /**
     * Get LSP servers for a workspace (loaded on demand when clicking "Servers" tab).
     */
    @GET
    @Path("/workspaces/{uri}/lsp-servers")
    public List<LspServerDTO> getLspServers(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);
        Workspace workspace = application.getWorkspace(uri);
        if (workspace == null) {
            throw new NotFoundException("Workspace not found: " + uri);
        }

        var serverConfigs = application.getLspServerConfigs();
        return serverConfigs.stream()
                .map(config -> serverDTOBuilder.buildRuntime(config, workspace))
                .toList();
    }

    /**
     * Get DAP sessions for a workspace (loaded on demand when clicking "Debuggers" tab).
     */
    @GET
    @Path("/workspaces/{uri}/dap-sessions")
    public List<Map<String, Object>> getDapSessions(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);
        Workspace workspace = application.getWorkspace(uri);
        if (workspace == null) {
            throw new NotFoundException("Workspace not found: " + uri);
        }

        return dapSessionManager.getSessionsForWorkspace(uri).stream()
                .map(session -> {
                    Map<String, Object> sessionInfo = new java.util.HashMap<>();
                    sessionInfo.put("sessionId", session.getSessionId());
                    sessionInfo.put("serverId", session.getServerConfig().getServerId());
                    sessionInfo.put("workspaceUri", session.getWorkspace().getNormalizedUri());
                    sessionInfo.put("language", session.getLanguage());
                    sessionInfo.put("sessionName", session.getSessionName());
                    sessionInfo.put("state", session.getState().name());
                    sessionInfo.put("createdBy", session.getCreatedBy());
                    sessionInfo.put("launchedBy", session.getLaunchedBy());
                    sessionInfo.put("debugMode", session.isDebugMode());
                    if (session.getCreatedAt() != null) {
                        sessionInfo.put("createdAt", session.getCreatedAt().toString());
                    }
                    if (session.getLaunchedAt() != null) {
                        sessionInfo.put("launchedAt", session.getLaunchedAt().toString());
                    }
                    if (session.getLaunchConfiguration() != null) {
                        sessionInfo.put("launchConfiguration", session.getLaunchConfiguration());
                    }
                    return sessionInfo;
                })
                .toList();
    }

    /**
     * Close a workspace: shutdown all its LSP servers and remove from memory.
     */
    @DELETE
    @Path("/workspaces/{uri}")
    public Response closeWorkspace(@PathParam("uri") String uriParam) {
        URI uri = URI.create(uriParam);

        application.closeWorkspace(uri).join();

        return Response.ok()
                .entity("{\"status\": \"closed\", \"uri\": \"" + uri + "\"}")
                .build();
    }

    private WorkspaceDTO toDTO(Workspace workspace) {
        var uri = workspace.getNormalizedUri();

        // Build MCP client info with timestamps
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        List<WorkspaceDTO.McpClientInfo> mcpClients = workspace.getMcpClientConnections().values().stream()
                .map(clientInfo -> new WorkspaceDTO.McpClientInfo(
                    clientInfo.name(),
                    formatter.format(clientInfo.connectedAt())
                ))
                .toList();

        LOG.infof("Workspace %s - mcpClients: %s", uri, mcpClients);
        return new WorkspaceDTO(uri, mcpClients);
    }
}
