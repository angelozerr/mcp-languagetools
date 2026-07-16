package com.redhat.mcp.languagetools.dap.server;

import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.settings.PathConfig;
import com.redhat.mcp.languagetools.server.ServerDescriptorLoaderBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads DAP server descriptors from JSON files.
 * Uses Gson exclusively for JSON parsing.
 */
@ApplicationScoped
public class DapServerDescriptorLoader extends ServerDescriptorLoaderBase<DapServerConfig> {

    private static final Logger LOG = Logger.getLogger(DapServerDescriptorLoader.class);

    // JSON field names
    private static final String FIELD_LAUNCH = "launch";
    private static final String FIELD_ATTACH = "attach";
    private static final String FIELD_DEBUG_SERVER_READY_PATTERN = "debugServerReadyPattern";
    private static final String FIELD_ENV = "env";
    private static final String FIELD_WORKING_DIRECTORY = "workingDirectory";

    public DapServerDescriptorLoader() {
        super();
    }

    @Override
    public String getRoot() {
        return PathConfig.getDapDirName();
    }

    @Override
    protected DapServerConfig createConfig(String serverId, Application application) {
        return new DapServerConfig(serverId, application.getPathManager().getDapServerHome(serverId), application);
    }

    @Override
    protected JsonObject loadServer(String serverId, Path serverDir, DapServerConfig config) throws IOException {
        JsonObject jsonObject =  super.loadServer(serverId, serverDir, config);

        // Launch commands (OS-specific)
        if (jsonObject.has(FIELD_LAUNCH)) {
            Map<String, String> launch = new HashMap<>();
            jsonObject.getAsJsonObject(FIELD_LAUNCH).entrySet().forEach(entry ->
                    launch.put(entry.getKey(), entry.getValue().getAsString())
            );
            config.setLaunch(launch);
        }

        // Attach configuration
        if (jsonObject.has(FIELD_ATTACH)) {
            Map<String, Object> attach = gson.fromJson(
                    jsonObject.get(FIELD_ATTACH),
                    Map.class
            );
            config.setAttach(attach);
        }

        // Debug server ready pattern
        if (jsonObject.has(FIELD_DEBUG_SERVER_READY_PATTERN)) {
            config.setDebugServerReadyPattern(jsonObject.get(FIELD_DEBUG_SERVER_READY_PATTERN).getAsString());
        }

        // Environment variables
        if (jsonObject.has(FIELD_ENV)) {
            Map<String, Object> env = gson.fromJson(
                    jsonObject.get(FIELD_ENV),
                    Map.class
            );
            config.setEnv(env);
        }

        // Working directory
        if (jsonObject.has(FIELD_WORKING_DIRECTORY)) {
            config.setWorkingDirectory(jsonObject.get(FIELD_WORKING_DIRECTORY).getAsString());
        }

        return jsonObject;
    }

}
