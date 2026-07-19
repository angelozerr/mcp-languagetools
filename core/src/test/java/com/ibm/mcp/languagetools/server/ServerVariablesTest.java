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
package com.ibm.mcp.languagetools.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerVariablesTest {

    private static ServerConfigBase createConfig(Path serverHome) {
        return new ServerConfigBase("test-server", serverHome, null);
    }

    // --- populate ---

    @Test
    void populateSetsServerHome() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        Map<String, String> variables = new HashMap<>();
        ServerVariables.populate(config, variables);
        assertEquals("/opt/servers/lemminx", variables.get("SERVER_HOME").replace("\\", "/"));
    }

    // --- resolve ---

    @Test
    void resolveNull() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        assertNull(ServerVariables.resolve(null, config));
    }

    @Test
    void resolveNoDollar() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        String template = "/some/plain/path";
        assertSame(template, ServerVariables.resolve(template, config));
    }

    @Test
    void resolveServerHome() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        String result = ServerVariables.resolve("$SERVER_HOME$/bin/lemminx", config);
        assertEquals("/opt/servers/lemminx/bin/lemminx", result.replace("\\", "/"));
    }

    @Test
    void resolveMultipleOccurrences() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        String result = ServerVariables.resolve("$SERVER_HOME$/bin:$SERVER_HOME$/lib", config);
        assertEquals("/opt/servers/lemminx/bin:/opt/servers/lemminx/lib", result.replace("\\", "/"));
    }

    @Test
    void resolveUnknownVariableKept() {
        ServerConfigBase config = createConfig(Path.of("/opt/servers/lemminx"));
        String result = ServerVariables.resolve("$UNKNOWN_VAR$", config);
        assertEquals("$UNKNOWN_VAR$", result);
    }
}
