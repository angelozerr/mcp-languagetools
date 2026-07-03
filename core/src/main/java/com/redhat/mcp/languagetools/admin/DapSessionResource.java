package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.admin.dto.CreateDapSessionRequest;
import com.redhat.mcp.languagetools.admin.dto.ErrorResponse;
import com.redhat.mcp.languagetools.dap.session.DapSession;
import com.redhat.mcp.languagetools.dap.session.DapSessionManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.Map;

@Path("/api/admin/dap/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DapSessionResource {

    private static final Logger LOG = Logger.getLogger(DapSessionResource.class);

    @Inject
    DapSessionManager sessionManager;

    /**
     * Create a new DAP session for testing.
     */
    @POST
    public Response createSession(CreateDapSessionRequest request) {
        LOG.infof("Creating DAP session: workspace=%s, dapServerId=%s, name=%s",
                request.workspaceUri(), request.dapServerId(), request.sessionName());

        try {
            URI workspaceUri = URI.create(request.workspaceUri());

            DapSession session = sessionManager.createSession(
                    workspaceUri,
                    request.dapServerId(),
                    request.sessionName()
            );

            // Return session info
            var response = Map.of(
                    "sessionId", session.getSessionId(),
                    "sessionName", session.getSessionName(),
                    "dapServerId", session.getServerConfig().getServerId(),
                    "state", session.getState().name(),
                    "language", session.getLanguage()
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to create DAP session");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.fromException(e))
                    .build();
        }
    }

    /**
     * Launch a DAP session with the provided configuration.
     */
    @POST
    @Path("/{sessionId}/launch")
    public Response launchSession(
            @PathParam("sessionId") String sessionId,
            @QueryParam("debugMode") @DefaultValue("true") boolean debugMode,
            Map<String, Object> launchConfig) {

        LOG.infof("Launching DAP session: sessionId=%s, debugMode=%s, config=%s", sessionId, debugMode, launchConfig);

        try {
            DapSession session = sessionManager.getSession(sessionId);
            if (session == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Session not found: " + sessionId))
                        .build();
            }

            // Launch the session asynchronously (don't block HTTP thread!)
            session.launch(launchConfig, debugMode).whenComplete((result, error) -> {
                if (error != null) {
                    LOG.errorf(error, "DAP session launch failed: %s", sessionId);
                } else {
                    LOG.infof("DAP session launched successfully: %s", sessionId);
                }
            });

            // Return immediately - client will get status updates via WebSocket
            return Response.accepted(Map.of(
                    "status", "launching",
                    "sessionId", sessionId,
                    "debugMode", debugMode,
                    "message", "Launch started, monitor status via WebSocket"
            )).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to start DAP session launch");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.fromException(e))
                    .build();
        }
    }

    /**
     * Stop a running DAP session.
     */
    @POST
    @Path("/{sessionId}/stop")
    public Response stopSession(@PathParam("sessionId") String sessionId) {
        LOG.infof("Stopping DAP session: sessionId=%s", sessionId);

        try {
            DapSession session = sessionManager.getSession(sessionId);
            if (session == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Session not found: " + sessionId))
                        .build();
            }

            // Terminate the session asynchronously
            session
                    .terminate()
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            LOG.errorf(error, "DAP session terminate failed: %s", sessionId);
                        } else {
                            LOG.infof("DAP session terminated successfully: %s", sessionId);
                        }
                    });

            // Return immediately
            return Response.accepted(Map.of(
                    "status", "stopping",
                    "sessionId", sessionId,
                    "message", "Stop requested"
            )).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to stop DAP session");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.fromException(e))
                    .build();
        }
    }

    /**
     * Delete a DAP session.
     */
    @DELETE
    @Path("/{sessionId}")
    public Response deleteSession(@PathParam("sessionId") String sessionId) {
        LOG.infof("Deleting DAP session: sessionId=%s", sessionId);

        try {
            DapSession session = sessionManager.getSession(sessionId);
            if (session == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Session not found: " + sessionId))
                        .build();
            }

            sessionManager.removeSession(sessionId);

            return Response.ok(Map.of(
                    "status", "deleted",
                    "sessionId", sessionId
            )).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete DAP session");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
