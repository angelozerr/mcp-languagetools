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
package com.ibm.mcp.languagetools.tools;

/**
 * Centralized descriptions for MCP tool arguments.
 * Avoids duplication across multiple @ToolArg annotations.
 */
public final class ToolArgDescriptions {

    private ToolArgDescriptions() {
    }

    // Workspace location arguments
    public static final String CWD =
        "Current working directory (project root path). " +
        "Example: '/home/user/project' or 'C:\\Users\\project'";

    // File URI arguments
    public static final String FILE_URI =
        "File URI (must be file:// URI as in LSP). " +
        "Example: 'file:///home/user/project/src/main/java/Main.java'";

    // Position arguments
    public static final String POSITION_LINE = "Line number (0-based)";
    public static final String POSITION_CHARACTER = "Character position in the line (0-based)";

    public static final String CANCELLATION = "Cancellation operation";

    public static final String OPEN_DOCUMENT_HINT =
        " For multiple operations on the same file, use open_document first to avoid repeated open/close cycles, then close_document when done.";
}
