package com.redhat.mcp.languagetools.lsp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.PathManager;
import com.redhat.mcp.languagetools.config.PathConfig;
import com.redhat.mcp.languagetools.lsp.Contributes;
import com.redhat.mcp.languagetools.server.ServerDescriptorLoaderBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
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
    private static final String FIELD_CONTRIBUTES = "contributes";

    // Extension (ex: Quarkus, Liberty LemMinx)
    private static final String PARENT_SERVER_ID = "parentServerId" ;

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
        // Command
        if (jsonObject.has(PARENT_SERVER_ID)) {
            // Server extension
            config.setParentServerId(jsonObject.get(PARENT_SERVER_ID).getAsString());
        } else
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

        // Contributions
        fillContributions(config, jsonObject);

        return jsonObject;
    }

    private void fillContributions(LspServerConfig config, JsonObject jsonObject) {
        // Contributions (LSP-specific)
        JsonElement contributesEl = jsonObject.get(FIELD_CONTRIBUTES);
        if (contributesEl != null) {
            if (contributesEl.isJsonObject()) {
                Contributes contributes = new Contributes();
                Map<String, JsonElement> contributionsMap = new HashMap<>();
                contributesEl.getAsJsonObject().entrySet().forEach(entry -> {
                    contributionsMap.put(entry.getKey(), entry.getValue());
                });
                contributes.setContributions(contributionsMap);
                config.setContributes(contributes);
            }
        }
    }

}
