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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for Java code search via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaCodeSearchTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_find_field_writes",
          description = "Find all write accesses to a field at a specific position. " +
                        "Returns every location where the field is assigned a value. " +
                        "Example: java_find_field_writes(cwd='/project', fileUri='file:///project/src/Model.java', line=5, character=10)")
    public CompletableFuture<String> findFieldWrites(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findFieldWrites",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_find_tests",
          description = "Find test methods in a Java file or across the workspace. " +
                        "Detects JUnit 4, JUnit 5, and TestNG test methods. " +
                        "Example: java_find_tests(cwd='/project', fileUri='file:///project/src/test/MyTest.java')")
    public CompletableFuture<String> findTests(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findTests",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_find_affected_tests",
          description = "Find tests transitively affected by changes to a symbol at a specific position. " +
                        "Returns test methods that directly or indirectly depend on the symbol. " +
                        "Example: java_find_affected_tests(cwd='/project', fileUri='file:///project/src/Service.java', line=15, character=10)")
    public CompletableFuture<String> findAffectedTests(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findAffectedTests",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_find_unused_code",
          description = "Find unused code in a Java file (unused imports, private fields, methods, local variables). " +
                        "Example: java_find_unused_code(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> findUnusedCode(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findUnusedCode",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_find_unreachable_code",
          description = "Find unreachable code in a Java file (dead code after return/throw, unreachable branches). " +
                        "Example: java_find_unreachable_code(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> findUnreachableCode(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findUnreachableCode",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_find_reflection_usage",
          description = "Find reflection API usage in a Java file (Class.forName, Method.invoke, Field access, etc.). " +
                        "Example: java_find_reflection_usage(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> findReflectionUsage(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findReflectionUsage",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_suggest_imports",
          description = "Find import candidates for an unresolved type name. " +
                        "Returns all fully qualified names matching the simple type name. " +
                        "Example: java_suggest_imports(cwd='/project', typeName='List')")
    public CompletableFuture<String> suggestImports(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Simple type name to search for (e.g., 'List', 'Map')") String typeName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.suggestImports",
                Map.of("typeName", typeName),
                cancellation, progress);
    }

    @Tool(name = "java_get_type_usage_summary",
          description = "Get a comprehensive usage summary for a Java type. " +
                        "Returns counts and locations for all usage kinds: references, instantiations, casts, " +
                        "instanceof checks, annotations, and type arguments. " +
                        "Example: java_get_type_usage_summary(cwd='/project', fullyQualifiedName='java.util.ArrayList')")
    public CompletableFuture<String> getTypeUsageSummary(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the type") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.getTypeUsageSummary",
                Map.of("fullyQualifiedName", fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_search_symbols",
          description = "Search for Java symbols (types, methods, fields) by name pattern. " +
                        "Supports glob patterns (e.g., 'My*Service'). " +
                        "Example: java_search_symbols(cwd='/project', query='UserService')")
    public CompletableFuture<String> searchSymbols(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Search query string or glob pattern") String query,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.searchSymbols",
                Map.of("query", query),
                cancellation, progress);
    }

    @Tool(name = "java_find_references",
          description = "Find all references to a Java symbol at a specific position. " +
                        "Returns enriched reference information with Java-specific context. " +
                        "Example: java_find_references(cwd='/project', fileUri='file:///project/src/Service.java', line=10, character=5)")
    public CompletableFuture<String> findReferences(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findReferences",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_find_implementations",
          description = "Find all implementations of a Java interface or abstract class. " +
                        "Returns concrete classes that implement or extend the type. " +
                        "Example: java_find_implementations(cwd='/project', fullyQualifiedName='com.example.Service')")
    public CompletableFuture<String> findImplementations(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the interface or abstract class") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.findImplementations",
                Map.of("fullyQualifiedName", fullyQualifiedName),
                cancellation, progress);
    }
}
