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

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.lsp4j.*;
import org.jboss.logging.Logger;

import com.redhat.mcp.languagetools.lsp.LspServer;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import com.redhat.mcp.languagetools.workspace.Workspace;
import com.redhat.mcp.languagetools.workspace.WorkspaceManager;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * MCP tools for LSP references (find all references).
 */
@ApplicationScoped
public class ReferencesTools {

    private static final Logger LOG = Logger.getLogger(ReferencesTools.class);

    @Inject
    WorkspaceManager workspaceManager;

    @Inject
    com.redhat.mcp.languagetools.language.LanguageRegistry languageRegistry;

    @Tool(description = "Find all references to a symbol at a specific position in a file. " +
                        "Returns all locations where the symbol is used across the workspace. " +
                        "Example: findReferences(cwd='/home/user/project', fileUri='file:///home/user/project/src/Main.java', line=10, character=5)")
    public String findReferences(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = "Line number (0-based)") int line,
            @ToolArg(description = "Character position in the line (0-based)") int character) {
        try {
            URI uri = URI.create(fileUri);
            LOG.infof("Finding references at: %s:%d:%d (from cwd: %s)", uri, line, character, cwd);

            Workspace ws = workspaceManager.getWorkspaceForFile(uri).join();

            // Detect language from file
            java.util.Optional<String> languageId = languageRegistry.detectLanguage(uri);
            if (languageId.isEmpty()) {
                LOG.debugf("No language detected for: %s", uri);
                return "No language detected for: " + fileUri;
            }

            String language = languageId.get();
            LOG.debugf("Detected language '%s' for: %s", language, uri);

            Map<String, LspServer> servers = ws.getAllLspServers();
            if (servers.isEmpty()) {
                return "No language servers available in workspace";
            }

            // Find the server that handles this file
            LspServer server = null;
            for (LspServer s : servers.values()) {
                if (s.getConfig().canHandle(fileUri, language)) {
                    server = s;
                    break;
                }
            }

            if (server == null) {
                return "No language server found for: " + fileUri;
            }

            // Build references parameters
            ReferenceParams params = new ReferenceParams();
            params.setTextDocument(new TextDocumentIdentifier(fileUri));
            params.setPosition(new Position(line, character));

            ReferenceContext context = new ReferenceContext();
            context.setIncludeDeclaration(true); // Include the declaration itself
            params.setContext(context);

            // Call textDocument/references
            List<? extends Location> references = server.getLanguageServer()
                    .getTextDocumentService()
                    .references(params)
                    .join();

            if (references == null || references.isEmpty()) {
                return String.format("No references found for symbol at %s:%d:%d", fileUri, line + 1, character);
            }

            // Format results
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d reference(s) for symbol at %s:%d:%d\n\n",
                    references.size(), fileUri, line + 1, character));

            // Group by file
            Map<String, List<Location>> byFile = references.stream()
                    .collect(java.util.stream.Collectors.groupingBy(Location::getUri));

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

        } catch (Exception e) {
            LOG.error("Failed to find references", e);
            return "Failed to find references: " + e.getMessage();
        }
    }
}
