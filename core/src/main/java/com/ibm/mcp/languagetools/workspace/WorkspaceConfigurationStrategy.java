package com.ibm.mcp.languagetools.workspace;

/**
 * Strategy for loading workspace configuration from multiple providers.
 */
public enum WorkspaceConfigurationStrategy {

    /**
     * Use the first provider whose settings file exists.
     */
    FIRST_FOUND,

    /**
     * Merge settings from all providers whose files exist.
     * Earlier providers in the list have higher priority (their values win on conflicts).
     */
    MERGE
}
