package com.redhat.mcp.languagetools.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.admin.dto.ContributionDTOBuilder;
import com.redhat.mcp.languagetools.admin.dto.DapConfigDTO;
import com.redhat.mcp.languagetools.admin.dto.ErrorResponse;
import com.redhat.mcp.languagetools.dap.server.DapServerConfig;
import com.redhat.mcp.languagetools.Application;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint for all DAP-related admin operations.
 * Consolidates DAP config listing, details, installer, sessions, and templates.
 */
@Path("/api/admin/dap")
@Produces(MediaType.APPLICATION_JSON)
public class DapAdminResource {

    private static final Logger LOG = Logger.getLogger(DapAdminResource.class);

    @Inject
    Application application;

    @Inject
    PathManager pathManager;

    @Inject
    ContributionDTOBuilder contributionBuilder;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ========== DAP Configs ==========

    /**
     * List all configured DAP servers (static config).
     */
    @GET
    @Path("/configs")
    public List<DapConfigDTO> listConfigs() {
        return application.getDapServerConfigs()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get details of a specific DAP config.
     */
    @GET
    @Path("/configs/{serverId}")
    public DapConfigDTO getConfig(@PathParam("serverId") String serverId) {
        DapServerConfig config = application.getDapServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("DAP server not found: " + serverId);
        }

        return toDTO(config);
    }

    private DapConfigDTO toDTO(DapServerConfig config) {
        return new DapConfigDTO(
            config.getServerId(),
            config.getName(),
            config.getDescription(),
            config.getDocumentSelector(),
            contributionBuilder.buildContributions(config)
        );
    }

    // ========== DAP Installer ==========

    /**
     * Get installer.json for a DAP server.
     */
    @GET
    @Path("/configs/{serverId}/installer")
    public Response getInstaller(@PathParam("serverId") String serverId) {
        DapServerConfig config = application.getDapServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("DAP server not found: " + serverId);
        }

        var installerConfig = config.getInstallerConfig();
        if (installerConfig == null) {
            return Response.ok("{}").build();
        }

        return Response.ok(gson.toJson(installerConfig)).build();
    }

    /**
     * Save installer.json for a DAP server.
     */
    @POST
    @Path("/configs/{serverId}/installer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveInstaller(@PathParam("serverId") String serverId, JsonObject installerJson) {
        DapServerConfig config = application.getDapServerConfig(serverId);

        if (config == null) {
            throw new NotFoundException("DAP server not found: " + serverId);
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

    // ========== DAP Launch Configuration Templates ==========

    /**
     * Get launch configuration templates for a DAP server.
     */
    @GET
    @Path("/configs/{serverId}/templates")
    public Response getTemplates(@PathParam("serverId") String serverId) {
        DapServerConfig serverConfig = application.getDapServerConfig(serverId);

        if (serverConfig == null) {
            throw new NotFoundException("DAP server not found: " + serverId);
        }

        List<Map<String, Object>> templates = serverConfig.getConfigurationTemplates();
        return Response.ok(Map.of("serverId", serverId, "templates", templates)).build();
    }

    // Note: DAP sessions endpoints are in DapSessionResource (moved there)
}
