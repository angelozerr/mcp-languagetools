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
package com.ibm.mcp.languagetools.lsp.tools.params;

/**
 * Base class for all LSP request parameters.
 * Contains the workspace root (cwd).
 */
public class LspRequestParams {

    private final String cwd;

    public LspRequestParams(String cwd) {
        this.cwd = cwd;
    }

    public String getCwd() {
        return cwd;
    }
}
