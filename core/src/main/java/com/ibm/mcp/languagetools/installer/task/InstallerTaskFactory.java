/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.ibm.mcp.languagetools.installer.download.GitHubAssetFetcherManager;
import com.ibm.mcp.languagetools.installer.download.MavenArtifactFetcherManager;
import com.ibm.mcp.languagetools.installer.download.OpenVsxAssetFetcherManager;

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
