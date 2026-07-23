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
package com.ibm.mcp.languagetools.extensions.jdtls.tools;

import com.ibm.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for Java navigation via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaNavigationTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_go_to_definition",
          description = "Navigate to the definition of a Java symbol")
    public CompletableFuture<String> goToDefinition(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GO_TO_DEFINITION,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_hover_info",
          description = "Get rich hover information for a Java symbol (signature, Javadoc, type info)")
    public CompletableFuture<String> getHoverInfo(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_HOVER_INFO,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_javadoc",
          description = "Get parsed Javadoc documentation for a Java symbol")
    public CompletableFuture<String> getJavadoc(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_JAVADOC,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_symbol_info",
          description = "Get detailed information about any Java symbol at a position")
    public CompletableFuture<String> getSymbolInfo(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_SYMBOL_INFO,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_enclosing_element",
          description = "Get the enclosing method, class, and package for a position")
    public CompletableFuture<String> getEnclosingElement(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_ENCLOSING_ELEMENT,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_field_at_position",
          description = "Get field information at a specific position in a Java file")
    public CompletableFuture<String> getFieldAtPosition(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_FIELD_AT_POSITION,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_method_at_position",
          description = "Get method information at a specific position in a Java file")
    public CompletableFuture<String> getMethodAtPosition(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_METHOD_AT_POSITION,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_type_at_position",
          description = "Get type information at a specific position in a Java file")
    public CompletableFuture<String> getTypeAtPosition(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_TYPE_AT_POSITION,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_signature_help",
          description = "Get method signature help at a specific position")
    public CompletableFuture<String> getSignatureHelp(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_SIGNATURE_HELP,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_super_method",
          description = "Find the method that a Java method overrides or implements")
    public CompletableFuture<String> getSuperMethod(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_SUPER_METHOD,
                RefactoringHelper.positionParams(fileUri, line, character),
                cancellation, progress);
    }

    @Tool(name = "java_get_document_symbols",
          description = "Get all symbols (types, methods, fields) in a Java file")
    public CompletableFuture<String> getDocumentSymbols(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.GET_DOCUMENT_SYMBOLS, uris,
                    uri -> Map.of("uri", uri), cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.GET_DOCUMENT_SYMBOLS,
                Map.of("uri", fileUri),
                cancellation, progress);
    }
}
