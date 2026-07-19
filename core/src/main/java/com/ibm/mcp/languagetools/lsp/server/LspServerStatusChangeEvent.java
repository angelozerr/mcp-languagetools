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
package com.ibm.mcp.languagetools.lsp.server;

import com.ibm.mcp.languagetools.server.ServerStatus;

import java.net.URI;

/**
 * CDI event fired when an LSP server status changes.
 */
public record LspServerStatusChangeEvent(
    URI workspaceUri,
    String serverId,
    ServerStatus oldStatus,
    ServerStatus newStatus
) {
}
