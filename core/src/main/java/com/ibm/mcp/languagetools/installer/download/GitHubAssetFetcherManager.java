/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.ibm.mcp.languagetools.installer.download;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager responsible for providing and caching {@link GitHubAssetFetcher} instances.
 *
 * <p>This class ensures that a unique {@link GitHubAssetFetcher} is created per GitHub repository
 * (identified by owner and repository name) and reused thereafter.</p>
 */
public class GitHubAssetFetcherManager {

    private static final GitHubAssetFetcherManager INSTANCE = new GitHubAssetFetcherManager();

    private final Map<String, GitHubAssetFetcher> assetFetchers = new ConcurrentHashMap<>();

    private GitHubAssetFetcherManager() {
    }

    public static GitHubAssetFetcherManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a {@link GitHubAssetFetcher} for the specified GitHub repository.
     *
     * <p>If a fetcher for the given owner and repository already exists, it is returned.
     * Otherwise, a new fetcher is created, cached, and returned.</p>
     *
     * @param owner the GitHub repository owner (username or organization)
     * @param repository the GitHub repository name
     * @return a cached or newly created {@link GitHubAssetFetcher} for the specified repository
     */
    public GitHubAssetFetcher getAssetFetcher(String owner, String repository) {
        String key = owner + "#" + repository;
        return assetFetchers.computeIfAbsent(key, k -> new GitHubAssetFetcher(owner, repository));
    }
}
