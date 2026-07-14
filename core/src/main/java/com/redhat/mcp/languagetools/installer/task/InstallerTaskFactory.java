package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.redhat.mcp.languagetools.installer.download.GitHubAssetFetcherManager;
import com.redhat.mcp.languagetools.installer.download.MavenArtifactFetcherManager;
import com.redhat.mcp.languagetools.installer.download.OpenVsxAssetFetcherManager;

/**
 * Factory for creating installer tasks from JSON.
 * Uses Gson for JSON parsing.
 */
public interface InstallerTaskFactory {
    /**
     * Gets the task type this factory handles (e.g., "download", "fileExists").
     */
    String getType();

    /**
     * Creates a task from JSON configuration.
     *
     * @param config JSON configuration for the task (Gson JsonElement)
     * @return The created task
     */
    InstallerTask createTask(JsonElement config);

    /**
     * Gets the GitHubAssetFetcherManager singleton.
     */
    static GitHubAssetFetcherManager getGitHubAssetFetcherManager() {
        return GitHubAssetFetcherManager.getInstance();
    }

    /**
     * Gets the MavenArtifactFetcherManager singleton.
     */
    static MavenArtifactFetcherManager getMavenArtifactFetcherManager() {
        return MavenArtifactFetcherManager.getInstance();
    }

    /**
     * Gets the OpenVsxAssetFetcherManager singleton.
     */
    static OpenVsxAssetFetcherManager getOpenVsxAssetFetcherManager() {
        return OpenVsxAssetFetcherManager.getInstance();
    }
}
