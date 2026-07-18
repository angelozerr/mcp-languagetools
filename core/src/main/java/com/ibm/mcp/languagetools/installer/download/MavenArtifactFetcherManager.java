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
 * Singleton manager for providing and caching {@link MavenArtifactFetcher} instances.
 */
public class MavenArtifactFetcherManager {

    private static final MavenArtifactFetcherManager INSTANCE = new MavenArtifactFetcherManager();

    private final Map<String, MavenArtifactFetcher> artifactFetchers = new ConcurrentHashMap<>();

    private MavenArtifactFetcherManager() {
    }

    public static MavenArtifactFetcherManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a {@link MavenArtifactFetcher} for the specified Maven artifact.
     *
     * <p>If a fetcher for the given groupId and artifactId already exists, it is returned.
     * Otherwise, a new fetcher is created, cached, and returned.</p>
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @return a cached or newly created {@link MavenArtifactFetcher} for the specified artifact
     */
    public MavenArtifactFetcher getArtifactFetcher(String groupId, String artifactId) {
        String key = groupId + "#" + artifactId;
        return artifactFetchers.computeIfAbsent(key, k -> new MavenArtifactFetcher(groupId, artifactId));
    }
}
