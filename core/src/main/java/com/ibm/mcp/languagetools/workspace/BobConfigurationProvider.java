package com.ibm.mcp.languagetools.workspace;

import java.nio.file.Path;

/**
 * Workspace configuration provider for Bob (.bob/settings.json).
 */
public class BobConfigurationProvider implements WorkspaceConfigurationProvider {

    @Override
    public String getId() {
        return "bob";
    }

    @Override
    public Path getSettingsFile(Path workspaceRoot) {
        return workspaceRoot.resolve(".bob").resolve("settings.json");
    }
}
