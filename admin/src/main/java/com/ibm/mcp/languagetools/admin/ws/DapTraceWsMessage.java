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
package com.ibm.mcp.languagetools.admin.ws;

import com.ibm.mcp.languagetools.trace.TraceCollector;

public class DapTraceWsMessage extends TraceWsMessage {

    private final String workspaceUri;
    private final String serverId;
    private final String sessionId;

    public DapTraceWsMessage(String workspaceUri, String serverId, String sessionId, String content, TraceCollector.MessageType messageType) {
        super(WsMessageType.DAP_TRACE, content, messageType);
        this.workspaceUri = workspaceUri;
        this.serverId = serverId;
        this.sessionId = sessionId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getServerId() {
        return serverId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
