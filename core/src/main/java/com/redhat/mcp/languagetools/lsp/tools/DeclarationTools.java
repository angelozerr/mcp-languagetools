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
package com.redhat.mcp.languagetools.lsp.tools;

import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.tools.params.FilePositionRequestParams;
import com.redhat.mcp.languagetools.lsp.tools.strategies.DeclarationStrategy;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for LSP declaration (go to declaration).
 */
@ApplicationScoped
public class DeclarationTools {

    @Inject
    LspRequestExecutor requestExecutor;

    @Inject
    LanguageRegistry languageRegistry;

    @Tool(description = "Go to the declaration of a symbol at a specific position in a file. " +
                        "Returns the location where the symbol is declared (different from definition for forward declarations). " +
                        "Example: goToDeclaration(cwd='/home/user/project', fileUri='file:///home/user/project/src/Main.cpp', line=10, character=5)" +
                        ToolArgDescriptions.OPEN_DOCUMENT_HINT)
    public CompletableFuture<String> goToDeclaration(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = ToolArgDescriptions.CANCELLATION) Cancellation cancellation,
            Progress progress) {

        FilePositionRequestParams params = new FilePositionRequestParams(cwd, fileUri, line, character);
        return requestExecutor.execute(
                params,
                new DeclarationStrategy(languageRegistry),
                cancellation,
                progress);
    }
}
