package com.ibm.mcp.languagetools.workspace;

import com.ibm.mcp.languagetools.settings.AbstractConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Workspace configuration reader.
 * Reads settings from .vscode/settings.json or .bob/settings.json (first found).
 */
public class WorkspaceConfiguration extends AbstractConfiguration {

    private static final String SETTINGS_FILE = "settings.json";

    private final Path workspaceRoot;

    public WorkspaceConfiguration(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        load();
    }

    @Override
    protected Path getSettingsFile() {
        Path vscodeSettings = workspaceRoot.resolve(".vscode").resolve(SETTINGS_FILE);
        if (Files.exists(vscodeSettings)) {
            return vscodeSettings;
        }
        Path bobSettings = workspaceRoot.resolve(".bob").resolve(SETTINGS_FILE);
        if (Files.exists(bobSettings)) {
            return bobSettings;
        }
        return vscodeSettings;
    }

    /**
     * Reload settings from disk.
     */
    public void reload() {
        load();
    }
}
