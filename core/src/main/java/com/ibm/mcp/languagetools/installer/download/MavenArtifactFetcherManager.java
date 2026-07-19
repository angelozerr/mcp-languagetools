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
