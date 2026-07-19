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
package com.ibm.mcp.languagetools.dap.transport;

/**
 * DAP transport type.
 */
public enum TransportType {
    /**
     * Standard input/output (default).
     * The DAP client launches the server process and communicates via stdin/stdout.
     */
    STDIO,

    /**
     * TCP socket connection.
     * The DAP client connects to a server listening on a TCP port.
     */
    SOCKET;

    /**
     * Parse transport type from string.
     *
     * @param value the string value (case-insensitive)
     * @return the transport type, or STDIO if invalid
     */
    public static TransportType get(String value) {
        if (value == null) {
            return STDIO;
        }
        try {
            return TransportType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return STDIO;
        }
    }
}
