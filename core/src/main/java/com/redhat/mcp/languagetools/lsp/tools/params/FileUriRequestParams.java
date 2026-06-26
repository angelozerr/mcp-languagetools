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
 * Request parameters for file URI-based LSP requests.
 * Contains workspace root + file URI.
 */
public class FileUriRequestParams extends LspRequestParams {

    private final String fileUri;

    public FileUriRequestParams(String cwd, String fileUri) {
        super(cwd);
        this.fileUri = fileUri;
    }

    public String getFileUri() {
        return fileUri;
    }
}

