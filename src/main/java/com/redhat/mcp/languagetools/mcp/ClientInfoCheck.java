/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.mcp.languagetools.mcp;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * MCP InitialCheck that captures and broadcasts client information.
 */
@ApplicationScoped
public class ClientInfoCheck implements InitialCheck {

    private static final Logger LOG = Logger.getLogger(ClientInfoCheck.class);

    @Inject
    Event<McpClientConnectedEvent> clientConnectedEvent;

    @Override
    public Uni<CheckResult> perform(InitialRequest initialRequest) {
        try {
            // Extract client info from MCP initialize request
            String clientName = initialRequest.implementation().name();
            String clientVersion = initialRequest.implementation().version();
            String fullClientName = clientName + " " + clientVersion;

            LOG.infof("MCP client connected: %s (protocol: %s)",
                     fullClientName, initialRequest.protocolVersion());

            // Fire CDI event with client info
            clientConnectedEvent.fire(new McpClientConnectedEvent(fullClientName));

            return InitialCheck.CheckResult.success();

        } catch (Exception e) {
            LOG.warnf(e, "Failed to extract client info, allowing connection anyway");
            clientConnectedEvent.fire(new McpClientConnectedEvent("Unknown MCP Client"));
            return InitialCheck.CheckResult.success();
        }
    }
}

