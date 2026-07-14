package com.redhat.mcp.languagetools.installer.download;

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
