package com.ibm.mcp.languagetools.workspace;

import java.nio.file.Path;

/**
 * Workspace configuration provider for VS Code (.vscode/settings.json).
 */
public class VsCodeConfigurationProvider implements WorkspaceConfigurationProvider {

    @Override
    public String getId() {
        return "vscode";
    }

    @Override
    public Path getSettingsFile(Path workspaceRoot) {
        return workspaceRoot.resolve(".vscode").resolve("settings.json");
    }
}
