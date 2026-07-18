package com.ibm.mcp.languagetools;

import com.ibm.mcp.languagetools.settings.PathConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;

/**
 * Centralized path management for MCP Language Tools.
 * Handles all path resolution and variable substitution.
 * Uses PathConfig for configurable paths.
 */
@ApplicationScoped
public class PathManager {

    // File names (hardcoded constants)
    public static final String SETTINGS_JSON = "settings.json";

    @Inject
    PathConfig pathConfig;

    /**
     * Get the root directory for MCP Language Tools (~/.mcp-languagetools by default)
     */
    public Path getMcpLangToolsRoot() {
        return pathConfig.getMcpLangToolsDir();
    }

    // ----------------------- LSP configuration

    /**
     * Get the directory where LSP servers are installed (~/.mcp-languagetools/lsp)
     */
    public Path getLspServersDir() {
        return getMcpLangToolsRoot().resolve(PathConfig.getLspDirName());
    }

    /**
     * Get the home directory for a specific LSP server (~/.mcp-languagetools/lsp/{serverId})
     */
    public Path getLspServerHome(String serverId) {
        return getLspServersDir().resolve(serverId);
    }

    /**
     * Get the config directory for LSP servers (~/.mcp-languagetools/config/lsp)
     */
    public Path getLspConfigDir() {
        return getConfigDir().resolve(PathConfig.getLspDirName());
    }

    /**
     * Get the config directory for a specific LSP server (~/.mcp-languagetools/config/lsp/{serverId})
     */
    public Path getLspConfigDir(String serverId) {
        return getLspConfigDir().resolve(serverId);
    }

    // ----------------------- DAP configuration

    /**
     * Get the directory where DAP servers are installed (~/.mcp-languagetools/dap)
     */
    public Path getDapServersDir() {
        return getMcpLangToolsRoot().resolve(PathConfig.getDapDirName());
    }

    /**
     * Get the home directory for a specific DAP server (~/.mcp-languagetools/dap/{serverId})
     */
    public Path getDapServerHome(String serverId) {
        return getDapServersDir().resolve(serverId);
    }

    // Workspace configuration

    /**
     * Get the config directory root (~/.mcp-languagetools/config)
     */
    public Path getConfigDir() {
        return getMcpLangToolsRoot().resolve(pathConfig.getConfigDirName());
    }

    /**
     * Get the settings file path (~/.mcp-languagetools/settings.json)
     */
    public Path getSettingsFile() {
        return getMcpLangToolsRoot().resolve(SETTINGS_JSON);
    }

    /**
     * Get the workspace data directory (~/.mcp-languagetools/workspaces)
     */
    public Path getWorkspaceDataDir() {
        return getMcpLangToolsRoot().resolve("workspaces");
    }

    /**
     * Resolve variables in a template string.
     * Supports: $USER_HOME$, $SERVER_HOME$
     */
    public String resolveVariables(String template, String serverId) {
        if (template == null) {
            return null;
        }
        return template
            .replace("$USER_HOME$", pathConfig.getRootDir().toString())
            .replace("$SERVER_HOME$", getLspServerHome(serverId).toString());
    }
}
