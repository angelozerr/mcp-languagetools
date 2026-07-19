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
 * Singleton manager responsible for providing and caching {@link OpenVsxAssetFetcher} instances.
 *
 * <p>Ensures that a unique {@link OpenVsxAssetFetcher} is created per extension
 * (identified by namespace and extension name) and reused thereafter.</p>
 */
public class OpenVsxAssetFetcherManager {

    private static final OpenVsxAssetFetcherManager INSTANCE = new OpenVsxAssetFetcherManager();

    private final Map<String, OpenVsxAssetFetcher> assetFetchers = new ConcurrentHashMap<>();

    private OpenVsxAssetFetcherManager() {
    }

    public static OpenVsxAssetFetcherManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns an {@link OpenVsxAssetFetcher} for the specified extension.
     *
     * @param namespace the extension namespace (e.g., "vscjava")
     * @param extensionName the extension name (e.g., "vscode-java-debug")
     * @return a cached or newly created fetcher
     */
    public OpenVsxAssetFetcher getAssetFetcher(String namespace, String extensionName) {
        String key = namespace + "#" + extensionName;
        return assetFetchers.computeIfAbsent(key, k -> new OpenVsxAssetFetcher(namespace, extensionName));
    }
}
