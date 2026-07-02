package com.redhat.mcp.languagetools.installer;

import com.google.gson.JsonElement;

import java.nio.file.Path;

/**
 * Common interface for server configurations (LSP and DAP).
 * Provides access to installer configuration.
 */
public interface ServerConfig {

    /**
     * Get the server ID.
     */
    String getServerId();

    Path getServerHome();

    /**
     * Get the server name.
     */
    String getName();

    /**
     * Get the installer configuration as JSON.
     * Returns null if no installer is configured.
     */
    JsonElement getInstallerConfig();
}
