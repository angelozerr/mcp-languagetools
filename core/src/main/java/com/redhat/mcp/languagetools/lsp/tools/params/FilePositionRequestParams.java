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
 * Request parameters for file position-based LSP requests (references, definition, declaration, implementation, etc.).
 * Contains workspace root + file URI + line + character.
 */
public class FilePositionRequestParams extends FileUriRequestParams {

    private final int line;
    private final int character;

    public FilePositionRequestParams(String cwd, String fileUri, int line, int character) {
        super(cwd, fileUri);
        this.line = line;
        this.character = character;
    }

    public int getLine() {
        return line;
    }

    public int getCharacter() {
        return character;
    }
}

