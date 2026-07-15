package com.redhat.mcp.languagetools.lsp.server;

import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.settings.PathConfig;
import com.redhat.mcp.languagetools.server.ServerDescriptorLoaderBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads LSP server descriptors from JSON files.
 * Handles both server.json and server-extension.json.
 * Uses Gson exclusively for JSON parsing.
 */
@ApplicationScoped
public class LspServerDescriptorLoader extends ServerDescriptorLoaderBase<LspServerConfig> {

    // JSON field names
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_ENV = "env";
    private static final String FIELD_WORKING_DIRECTORY = "workingDirectory";
    private static final String FIELD_INITIALIZATION_OPTIONS = "initializationOptions";

    @Override
    public String getRoot() {
        return PathConfig.getLspDirName();
    }

    @Override
    protected LspServerConfig createConfig(String serverId, PathManager pathManager) {
        return new LspServerConfig(serverId, pathManager);
    }

    @Override
    protected JsonObject loadServer(String serverId, Path serverDir, LspServerConfig config) throws IOException {
        // server.json
        JsonObject jsonObject = super.loadServer(serverId, serverDir, config);

        if (jsonObject.has(FIELD_COMMAND)) {
            // Command
            config.setCommand(jsonObject.get(FIELD_COMMAND).getAsString());
            // Environment variables
            if (jsonObject.has(FIELD_ENV)) {
                Map<String, String> env = new HashMap<>();
                jsonObject.getAsJsonObject(FIELD_ENV).entrySet().forEach(entry ->
                        env.put(entry.getKey(), entry.getValue().getAsString())
                );
                config.setEnv(env);
            }

            // Working directory
            if (jsonObject.has(FIELD_WORKING_DIRECTORY)) {
                config.setWorkingDirectory(jsonObject.get(FIELD_WORKING_DIRECTORY).getAsString());
            }

            // Initialization options
            if (jsonObject.has(FIELD_INITIALIZATION_OPTIONS)) {
                Map<String, Object> initOptions = gson.fromJson(
                        jsonObject.get(FIELD_INITIALIZATION_OPTIONS),
                        Map.class
                );
                config.setInitializationOptions(initOptions);
            }
        }
        return jsonObject;
    }

}
