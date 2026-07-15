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

import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.tools.params.FilePositionRequestParams;
import org.eclipse.lsp4j.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Strategy for LSP textDocument/references requests.
 */
public class ReferencesStrategy extends FilePositionBasedStrategy<ReferenceParams, List<? extends Location>> {

    public ReferencesStrategy(LanguageRegistry languageRegistry) {
        super(languageRegistry, LspCapability.REFERENCES, "Find references");
    }

    @Override
    public ReferenceParams buildLspParams(FilePositionRequestParams params) {
        ReferenceParams lspParams = new ReferenceParams();
        lspParams.setTextDocument(new TextDocumentIdentifier(params.getFileUri()));
        lspParams.setPosition(new Position(params.getLine(), params.getCharacter()));

        ReferenceContext context = new ReferenceContext();
        context.setIncludeDeclaration(true); // Include the declaration itself
        lspParams.setContext(context);

        return lspParams;
    }

    @Override
    public CompletableFuture<List<? extends Location>> executeRequest(LspServer server, ReferenceParams lspParams) {
        return server.getLanguageServer()
                .getTextDocumentService()
                .references(lspParams);
    }

    @Override
    public List<? extends Location> getEmptyResult() {
        return Collections.emptyList();
    }

    @Override
    public boolean isValidResult(List<? extends Location> result) {
        return result != null && !result.isEmpty();
    }

    @Override
    public String formatResults(FilePositionRequestParams params, List<List<? extends Location>> results) {
        // Merge all results
        List<? extends Location> allReferences = results.stream()
                .flatMap(List::stream)
                .toList();

        // Deduplicate based on URI + range
        List<? extends Location> references = allReferences.stream()
                .distinct()
                .toList();

        // Format results
        StringBuilder result = new StringBuilder();
        result.append(String.format("Found %d reference(s) for symbol at %s:%d:%d\n\n",
                references.size(), params.getFileUri(), params.getLine() + 1, params.getCharacter()));

        // Group by file
        Map<String, List<Location>> byFile = references.stream()
                .collect(Collectors.groupingBy(Location::getUri));

        for (Map.Entry<String, List<Location>> entry : byFile.entrySet()) {
            String file = entry.getKey();
            List<Location> locations = entry.getValue();

            result.append(String.format("File: %s (%d reference(s))\n", file, locations.size()));

            for (Location location : locations) {
                Range range = location.getRange();
                result.append(String.format("  Line %d:%d-%d\n",
                        range.getStart().getLine() + 1,
                        range.getStart().getCharacter(),
                        range.getEnd().getCharacter()));
            }
            result.append("\n");
        }

        return result.toString();
    }

    @Override
    public String formatNoResultFound(FilePositionRequestParams params) {
        return String.format("No references found for symbol at %s:%d:%d",
                params.getFileUri(), params.getLine() + 1, params.getCharacter());
    }
}

