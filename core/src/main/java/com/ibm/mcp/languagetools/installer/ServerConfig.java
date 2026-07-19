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
package com.ibm.mcp.languagetools.installer;

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
