package com.ibm.mcp.languagetools.workspace;

import java.nio.file.Path;

/**
 * SPI for workspace configuration file providers.
 * Each provider knows how to locate a settings file within a workspace root
 * (e.g., .vscode/settings.json, .bob/settings.json).
 */
public interface WorkspaceConfigurationProvider {

    /**
     * Returns the unique identifier for this provider (e.g., "vscode", "bob").
     */
    String getId();

    /**
     * Returns the path to the settings file for the given workspace root.
     *
     * @param workspaceRoot the workspace root directory
     * @return the path to the settings file (may or may not exist on disk)
     */
    Path getSettingsFile(Path workspaceRoot);
}
