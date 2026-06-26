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

import com.redhat.mcp.languagetools.lsp.tools.params.WorkspaceSymbolRequestParams;
import com.redhat.mcp.languagetools.lsp.tools.strategies.WorkspaceSymbolStrategy;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for LSP workspace/symbol.
 */
@ApplicationScoped
public class WorkspaceSymbolTools {

    @Inject
    LspRequestExecutor requestExecutor;

    @Tool(description = "Search for symbols across the entire workspace. " +
                        "Returns symbols (classes, methods, variables, etc.) matching the query string. " +
                        "Example: searchWorkspaceSymbols(cwd='/home/user/project', query='MyClass')")
    public CompletableFuture<String> searchWorkspaceSymbols(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "The search query string to match against symbol names") String query,
            @ToolArg(description = ToolArgDescriptions.CANCELLATION) Cancellation cancellation) {

        WorkspaceSymbolRequestParams params = new WorkspaceSymbolRequestParams(cwd, query);
        return requestExecutor.execute(
                params,
                new WorkspaceSymbolStrategy(),
                cancellation
        );
    }
}
