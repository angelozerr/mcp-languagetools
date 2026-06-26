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
package com.redhat.mcp.languagetools.lsp.tools.params;

/**
 * Request parameters for workspace/symbol LSP requests.
 * Contains workspace root + search query.
 */
public class WorkspaceSymbolRequestParams extends LspRequestParams {

    private final String query;

    public WorkspaceSymbolRequestParams(String cwd, String query) {
        super(cwd);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
