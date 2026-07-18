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
package com.ibm.mcp.languagetools.lsp.tools.strategies;

import com.ibm.mcp.languagetools.language.LanguageDocument;
import com.ibm.mcp.languagetools.language.LanguageRegistry;
import com.ibm.mcp.languagetools.lsp.client.LspCapability;
import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.lsp.server.LspServerResolver;
import com.ibm.mcp.languagetools.lsp.tools.LspRequestExecutor;
import com.ibm.mcp.languagetools.lsp.tools.params.FilePositionRequestParams;
import com.ibm.mcp.languagetools.progress.ProgressMonitor;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import java.net.URI;
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
    private final String title;

    protected FilePositionBasedStrategy(LanguageRegistry languageRegistry, LspCapability capability, String title) {
        this.languageRegistry = languageRegistry;
        this.capability = capability;
        this.title = title;
    }

    @Override
    public LspCapability getCapability() {
        return capability;
    }

    @Override
    public String getTitle() {
        return title;
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
    public final CompletableFuture<TResult> executeRequest(LspServer server, TLspParams lspParams) {
        String fileUri = extractFileUri(lspParams);
        if (fileUri == null) {
            return doExecuteRequest(server, lspParams);
        }
        String languageId = languageRegistry.detectLanguage(URI.create(fileUri)).orElse("");
        return server.withAutoDidOpen(capability, fileUri, languageId,
                () -> doExecuteRequest(server, lspParams));
    }

    protected abstract CompletableFuture<TResult> doExecuteRequest(LspServer server, TLspParams lspParams);

    protected String extractFileUri(TLspParams lspParams) {
        if (lspParams instanceof TextDocumentPositionParams p) {
            return p.getTextDocument().getUri();
        }
        return null;
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
