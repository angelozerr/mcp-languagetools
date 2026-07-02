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
package com.redhat.mcp.languagetools.installer.download;

import com.google.gson.JsonObject;

import java.util.function.Function;

/**
 * Interface for fetching download URLs of assets from various sources.
 */
public interface AssetFetcher {

    /**
     * Interface for reporting progress and status messages.
     */
    interface Reporter {
        void setText(String text);
        void setText(String text, Exception e);
    }

    /**
     * Finds the download URL for an asset matching the given filters.
     *
     * @param releaseMatcher filter to select the desired release
     * @param assetMatcher filter to select the desired asset
     * @param reporter object to report progress or status messages
     * @return the download URL of the matching asset, or null if none found
     */
    String getDownloadUrl(Function<JsonObject, Boolean> releaseMatcher,
                                     Function<JsonObject, Boolean> assetMatcher,
                                     Reporter reporter);
}
