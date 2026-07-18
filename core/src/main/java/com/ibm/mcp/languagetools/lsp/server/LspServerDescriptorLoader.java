package com.ibm.mcp.languagetools.lsp.server;

import com.google.gson.JsonObject;
import com.ibm.mcp.languagetools.Application;
import com.ibm.mcp.languagetools.server.ServerDescriptorLoaderBase;
import com.ibm.mcp.languagetools.settings.PathConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads LSP server descriptors from JSON files.
 * Handles both server.json and server-extension.json.
 * Uses Gson exclusively for JSON parsing.
 */
@ApplicationScoped
public class LspServerDescriptorLoader extends ServerDescriptorLoaderBase<LspServerConfig> {

    // JSON field names
    private static final String FIELD_INITIALIZATION_OPTIONS = "initializationOptions";
    private static final String FIELD_TOOLS_REQUEST = "toolsRequest";
    private static final String FIELD_SKIP_DID_OPEN = "skipDidOpen";

    @Override
    public String getRoot() {
        return PathConfig.getLspDirName();
    }

    @Override
    protected String getCommandFieldName() {
        return "command";
    }

    @Override
    protected LspServerConfig createConfig(String serverId, Application application) {
        return new LspServerConfig(serverId, application);
    }

    @Override
    protected JsonObject loadServer(String serverId, Path serverDir, LspServerConfig config) throws IOException {
        // server.json
        JsonObject jsonObject = super.loadServer(serverId, serverDir, config);

        // Initialization options
        if (jsonObject.has(FIELD_INITIALIZATION_OPTIONS)) {
            Map<String, Object> initOptions = gson.fromJson(
                    jsonObject.get(FIELD_INITIALIZATION_OPTIONS),
                    Map.class
            );
            config.setInitializationOptions(initOptions);
        }

        // Tools request settings
        if (jsonObject.has(FIELD_TOOLS_REQUEST)) {
            JsonObject toolsRequest = jsonObject.getAsJsonObject(FIELD_TOOLS_REQUEST);
            if (toolsRequest.has(FIELD_SKIP_DID_OPEN)) {
                config.setSkipDidOpen(toolsRequest.get(FIELD_SKIP_DID_OPEN).getAsBoolean());
            }
        }

        return jsonObject;
    }

}
