package com.redhat.mcp.languagetools.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.redhat.mcp.languagetools.admin.dto.ErrorResponse;
import com.redhat.mcp.languagetools.admin.dto.LspConfigDTO;
import com.redhat.mcp.languagetools.admin.dto.ServerDTOBuilder;
import com.redhat.mcp.languagetools.admin.dto.StatusResponse;
import com.redhat.mcp.languagetools.installer.TaskRegistryInstaller;
import com.redhat.mcp.languagetools.installer.TraceProgressMonitor;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerConfig;
import com.redhat.mcp.languagetools.progress.ProgressBroadcaster;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressStep;
import com.redhat.mcp.languagetools.workspace.Workspace;
import com.redhat.mcp.languagetools.Application;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;

/**
 * REST endpoint for all LSP-related admin operations.
 * Consolidates LSP config listing, details, installer, control, and trace configuration.
 */
@Path("/api/admin/lsp")
@Produces(MediaType.APPLICATION_JSON)
public class LspAdminResource {

    private static final Logger LOG = Logger.getLogger(LspAdminResource.class);

    @Inject
    Application application;

    @Inject
    ServerDTOBuilder serverDTOBuilder;


    @Inject
    ProgressBroadcaster progressBroadcaster;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ========== LSP Configs ==========

    /**
     * List all configured LSP servers (static config, independent of workspaces).
     */
    @GET
    @Path("/configs")
    public List<LspConfigDTO> listConfigs() {
        LOG.info("listConfigs() called");
        try {
            var serverConfigs = application.getLspServerConfigs();
            LOG.infof("Found %d LSP configs", serverConfigs.size());

            var result = serverConfigs
                    .stream()
                    .map(serverDTOBuilder::buildConfig)
                    .toList();

            LOG.infof("Returning %d LSP configs", result.size());
            return result;
        } catch (Exception e) {
            LOG.error("Error in listConfigs", e);
            throw e;
        }
    }

    /**
     * Get details of a specific LSP config.
     */
    @GET
    @Path("/configs/{serverId}")
    public LspConfigDTO getConfig(@PathParam("serverId") String serverId) {
        LspServerConfig config = application.getLspServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("LSP server not found: " + serverId);
        }

        return serverDTOBuilder.buildConfig(config);
    }

    // ========== LSP Installer ==========

    /**
     * Get installer.json for a server.
     */
    @GET
    @Path("/configs/{serverId}/installer")
    public Response getInstaller(@PathParam("serverId") String serverId) {
        LspServerConfig config = application.getLspServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("LSP server not found: " + serverId);
        }

        var installerConfig = config.getInstallerConfig();
        if (installerConfig == null) {
            return Response.ok("{}").build();
        }

        return Response.ok(gson.toJson(installerConfig)).build();
    }

    /**
     * Save installer.json for a server.
     */
    @POST
    @Path("/configs/{serverId}/installer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveInstaller(@PathParam("serverId") String serverId, JsonObject installerJson) {
        LspServerConfig config = application.getLspServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("LSP server not found: " + serverId);
        }

        try {
            var serverHome = config.getServerHome();
            Files.createDirectories(serverHome);
            var installerPath = serverHome.resolve("installer.json");
            String json = gson.toJson(installerJson);
            Files.writeString(installerPath, json);

            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf("Failed to save installer.json for %s: %s", serverId, e.getMessage());
            return Response.status(500)
                    .entity(new ErrorResponse("Failed to save installer.json: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Run installer for an LSP server.
     */
    @POST
    @Path("/configs/{serverId}/install")
    public Response runInstaller(@PathParam("serverId") String serverId,
                                 @QueryParam("force") @DefaultValue("false") boolean force) {
        LspServerConfig config = application.getLspServerConfig(serverId);
        if (config == null) {
            throw new NotFoundException("LSP server not found: " + serverId);
        }
        if (config.getInstaller() == null) {
            return Response.status(404).entity(new ErrorResponse("No installer configured for: " + serverId)).build();
        }

        String taskId = "install-" + serverId;
        String title = "Install " + serverId;
        TraceProgressMonitor progressMonitor = new TraceProgressMonitor(
                config.getTraceCollector(), 100.0, progressBroadcaster, taskId, serverId, title);
        TaskRegistryInstaller.configureInstallerSteps(progressMonitor, config.getInstallerConfig(), force);
        progressMonitor.initializeSteps();

        config.resetInstallState();
        config.ensureInstalled(application.getPathManager(), null, progressMonitor, force)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        LOG.errorf(ex, "Failed to install server '%s'", serverId);
                        progressMonitor.setFailed(cause.getMessage());
                    } else {
                        progressMonitor.setComplete();
                    }
                });

        return Response.ok().entity(new StatusResponse("installing")).build();
    }

    // ========== LSP Server Control ==========

    /**
     * Stop an LSP server in a workspace.
     */
    @POST
    @Path("/servers/{workspaceUri}/{serverId}/stop")
    public Response stopServer(@PathParam("workspaceUri") String workspaceUriParam,
                                @PathParam("serverId") String serverId) {
        try {
            URI workspaceUri = URI.create(workspaceUriParam);
            Workspace workspace = application.getWorkspace(workspaceUri);

            if (workspace == null) {
                return Response.status(404).entity(new ErrorResponse("Workspace not found")).build();
            }

            var server = workspace.getLspServer(serverId);
            if (server == null) {
                return Response.status(404).entity(new ErrorResponse("Server not found")).build();
            }

            server.shutdown().join();
            return Response.ok().entity(new StatusResponse("stopped")).build();
        } catch (Exception e) {
            return Response.status(500).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    /**
     * Restart an LSP server in a workspace.
     */
    @POST
    @Path("/servers/{workspaceUri}/{serverId}/restart")
    public Response restartServer(@PathParam("workspaceUri") String workspaceUriParam,
                                   @PathParam("serverId") String serverId) {
        try {
            URI workspaceUri = URI.create(workspaceUriParam);
            Workspace workspace = application.getWorkspace(workspaceUri);

            if (workspace == null) {
                return Response.status(404).entity(new ErrorResponse("Workspace not found")).build();
            }

            // Restart from Admin UI - create ProgressMonitor with steps for user feedback
            LspServer server = workspace.getLspServer(serverId);
            String taskId = "restart-" + serverId;
            String title = "Restart " + serverId;
            TraceProgressMonitor progressMonitor = createServerStartMonitor(
                    server.getTraceCollector(), taskId, serverId, title);

            workspace.restartLspServer(serverId, progressMonitor)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            LOG.errorf(ex, "Failed to restart server '%s'", serverId);
                            progressMonitor.setFailed(ex.getMessage());
                        } else {
                            progressMonitor.setComplete();
                        }
                    });

            return Response.ok().entity(new StatusResponse("restarted")).build();
        } catch (Exception e) {
            return Response.status(500).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    /**
     * Start a managed LSP server in a workspace.
     */
    @POST
    @Path("/servers/{workspaceUri}/{serverId}/start-managed")
    public Response startManagedServer(@PathParam("workspaceUri") String workspaceUriParam,
                                        @PathParam("serverId") String serverId) {
        try {
            URI workspaceUri = URI.create(workspaceUriParam);
            Workspace workspace = application.getWorkspace(workspaceUri);

            if (workspace == null) {
                return Response.status(404).entity(new ErrorResponse("Workspace not found")).build();
            }

            var existingServer = workspace.getLspServer(serverId);
            if (existingServer != null) {
                // Start from Admin UI - create ProgressMonitor with steps for user feedback
                String taskId = "start-" + serverId;
                String title = "Start " + serverId;
                TraceProgressMonitor progressMonitor = createServerStartMonitor(
                        existingServer.getTraceCollector(), taskId, serverId, title);

                workspace.startManagedLspServer(serverId, progressMonitor)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                LOG.errorf(ex, "Failed to start server '%s'", serverId);
                                progressMonitor.setFailed(ex.getMessage());
                            } else {
                                progressMonitor.setComplete();
                            }
                        });
            } else {
                application.ensureServerStarted(serverId, workspaceUri);
            }

            return Response.ok().entity(new StatusResponse("starting")).build();
        } catch (Exception e) {
            return Response.status(500).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    /**
     * Disconnect from IDE instance.
     */
    @POST
    @Path("/servers/{workspaceUri}/{serverId}/disconnect")
    public Response disconnectFromIde(@PathParam("workspaceUri") String workspaceUriParam,
                                       @PathParam("serverId") String serverId) {
        try {
            URI workspaceUri = URI.create(workspaceUriParam);
            Workspace workspace = application.getWorkspace(workspaceUri);

            if (workspace == null) {
                return Response.status(404).entity(new ErrorResponse("Workspace not found")).build();
            }

            var server = workspace.getLspServer(serverId);
            if (server == null) {
                return Response.status(404).entity(new ErrorResponse("Server not found")).build();
            }

            server.shutdown().join();
            return Response.ok().entity(new StatusResponse("disconnected")).build();
        } catch (Exception e) {
            return Response.status(500).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    /**
     * Connect to IDE instance.
     */
    @POST
    @Path("/servers/{workspaceUri}/{serverId}/connect-ide")
    public Response connectToIde(@PathParam("workspaceUri") String workspaceUriParam,
                                  @PathParam("serverId") String serverId) {
        try {
            URI workspaceUri = URI.create(workspaceUriParam);
            Workspace workspace = application.getWorkspace(workspaceUri);

            if (workspace == null) {
                return Response.status(404).entity(new ErrorResponse("Workspace not found")).build();
            }

            var externalInstance = workspace.getExternalInstance(serverId);
            if (externalInstance == null) {
                return Response.status(404).entity(new ErrorResponse("No IDE instance available for this server")).build();
            }

            // Connect to IDE from Admin UI - create ProgressMonitor for user feedback
            LspServer server = workspace.getLspServer(serverId);
            String taskId = "connect-" + serverId;
            String title = "Connect " + serverId;
            ProgressMonitor progressMonitor = new TraceProgressMonitor(
                    server.getTraceCollector(), 100.0, progressBroadcaster, taskId, serverId, title);

            workspace.restartLspServer(serverId, progressMonitor).join();
            progressMonitor.setComplete();

            return Response.ok().entity(new StatusResponse("connected")).build();
        } catch (Exception e) {
            return Response.status(500).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    // ========== Progress Task Control ==========

    /**
     * Cancel a progress task (e.g., an installation).
     * Only works for tasks marked as cancellable (Admin-initiated tasks).
     */
    @POST
    @Path("/progress/{taskId}/cancel")
    public Response cancelTask(@PathParam("taskId") String taskId) {
        // Extract serverId from taskId (e.g., "install-jdtls" → "jdtls", "start-jdtls" → "jdtls")
        String serverId = taskId.replaceFirst("^(install|start|restart)-", "");

        var config = application.getLspServerConfig(serverId);
        if (config == null) {
            return Response.status(404).entity(new ErrorResponse("Server not found: " + serverId)).build();
        }

        var sharedProgress = config.getSharedInstallProgress();
        if (sharedProgress == null) {
            return Response.status(404).entity(new ErrorResponse("No active task to cancel")).build();
        }

        LOG.infof("Cancelling task '%s' for server '%s'", taskId, serverId);
        sharedProgress.cancel(taskId);

        return Response.ok().entity(new StatusResponse("cancelled")).build();
    }

    /**
     * Create a TraceProgressMonitor with standard server startup steps
     * (Installing → Starting → Initializing).
     */
    private TraceProgressMonitor createServerStartMonitor(
            com.redhat.mcp.languagetools.trace.TraceCollector traceCollector,
            String taskId, String serverId, String title) {
        TraceProgressMonitor monitor = new TraceProgressMonitor(
                traceCollector, 100.0, progressBroadcaster, taskId, serverId, title);
        monitor.addStep(ProgressStep.INSTALLING, 0.50);
        monitor.addStep(ProgressStep.STARTING, 0.10);
        monitor.addStep(ProgressStep.INITIALIZING, 0.40);
        monitor.initializeSteps();
        return monitor;
    }
}
