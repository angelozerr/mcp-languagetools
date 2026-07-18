/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.ibm.mcp.languagetools.lsp.client;

/**
 * LSP request constants.
 */
public class LspRequestConstants {

    // textDocument/* LSP requests
    public static final String TEXT_DOCUMENT_REFERENCES = "textDocument/references";
    public static final String TEXT_DOCUMENT_DEFINITION = "textDocument/definition";
    public static final String TEXT_DOCUMENT_DECLARATION = "textDocument/declaration";
    public static final String TEXT_DOCUMENT_IMPLEMENTATION = "textDocument/implementation";
    public static final String TEXT_DOCUMENT_DIAGNOSTIC = "textDocument/diagnostic";
    public static final String TEXT_DOCUMENT_HOVER = "textDocument/hover";
    public static final String TEXT_DOCUMENT_COMPLETION = "textDocument/completion";
    public static final String TEXT_DOCUMENT_DOCUMENT_SYMBOL = "textDocument/documentSymbol";
    public static final String TEXT_DOCUMENT_CODE_ACTION = "textDocument/codeAction";
    public static final String TEXT_DOCUMENT_RENAME = "textDocument/rename";

    // workspace/* LSP requests
    public static final String WORKSPACE_SYMBOL = "workspace/symbol";

    private LspRequestConstants() {
    }
}
