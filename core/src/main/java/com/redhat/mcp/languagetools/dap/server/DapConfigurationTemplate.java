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
package com.redhat.mcp.languagetools.dap.server;

import java.util.Map;

/**
 * A DAP configuration template loaded from /dap/{serverId}/*.json files.
 * <p>
 * Templates provide pre-configured launch/attach configurations for debug adapters.
 * The filename convention is: {@code launch.<name>.json} or {@code attach.<name>.json}
 * </p>
 */
public class DapConfigurationTemplate {

    /**
     * Template name derived from filename (e.g., "launch.program", "attach.port").
     */
    public String name;

    /**
     * Display label from JSON "name" field, or fallback to filename.
     */
    public String label;

    /**
     * The complete DAP configuration object.
     * Contains fields like: type, request, program, port, etc.
     */
    public Map<String, Object> body;

    public DapConfigurationTemplate(String name, String label, Map<String, Object> body) {
        this.name = name;
        this.label = label;
        this.body = body;
    }
}
