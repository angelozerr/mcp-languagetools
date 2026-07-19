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
package com.ibm.mcp.languagetools.lsp.client;

/**
 * LSP capability enum.
 */
public enum LspCapability {

    REFERENCES(LspRequestConstants.TEXT_DOCUMENT_REFERENCES),
    DEFINITION(LspRequestConstants.TEXT_DOCUMENT_DEFINITION),
    DECLARATION(LspRequestConstants.TEXT_DOCUMENT_DECLARATION),
    IMPLEMENTATION(LspRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION),
    DIAGNOSTIC(LspRequestConstants.TEXT_DOCUMENT_DIAGNOSTIC),
    HOVER(LspRequestConstants.TEXT_DOCUMENT_HOVER),
    COMPLETION(LspRequestConstants.TEXT_DOCUMENT_COMPLETION),
    DOCUMENT_SYMBOL(LspRequestConstants.TEXT_DOCUMENT_DOCUMENT_SYMBOL),
    CODE_ACTION(LspRequestConstants.TEXT_DOCUMENT_CODE_ACTION),
    RENAME(LspRequestConstants.TEXT_DOCUMENT_RENAME),
    WORKSPACE_SYMBOL(LspRequestConstants.WORKSPACE_SYMBOL);

    private final String method;

    LspCapability(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
