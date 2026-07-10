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
package com.redhat.mcp.languagetools.lsp.tools.strategies;

import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerResolver;
import com.redhat.mcp.languagetools.lsp.tools.LspRequestExecutor;
import com.redhat.mcp.languagetools.lsp.tools.params.FilePositionRequestParams;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for file position-based LSP request strategies.
 * Handles server resolution for file-based requests with position.
 *
 * @param <TLspParams> LSP request parameters type
 * @param <TResult>    LSP response type
 */
public abstract class FilePositionBasedStrategy<TLspParams, TResult>
        implements LspRequestExecutor.LspRequestStrategy<FilePositionRequestParams, TLspParams, TResult> {

    protected final LanguageRegistry languageRegistry;
    private final LspCapability capability;

    protected FilePositionBasedStrategy(LanguageRegistry languageRegistry, LspCapability capability) {
        this.languageRegistry = languageRegistry;
        this.capability = capability;
    }

    @Override
    public LspCapability getCapability() {
        return capability;
    }

    @Override
    public CompletableFuture<List<LspServer>> resolveServers(
            LspServerResolver resolver,
            FilePositionRequestParams params,
            ProgressMonitor progressMonitor) {

        // Create language document (detects language once)
        LanguageDocument document = languageRegistry.createDocument(params.getFileUri());

        // Resolve servers that support this capability for this file
        return resolver.getLspServersForFile(
                document,
                params.getCwd(),
                server -> server.isEnabled() && server.supportsCapability(getCapability(), document),
                progressMonitor
        );
    }

    @Override
    public String formatNoServerFound(FilePositionRequestParams params) {
        return String.format("No language server with %s support found for: %s", getCapability(), params.getFileUri());
    }

    @Override
    public String formatError(FilePositionRequestParams params, Throwable ex) {
        return "Failed to execute request: " + ex.getMessage();
    }
}
