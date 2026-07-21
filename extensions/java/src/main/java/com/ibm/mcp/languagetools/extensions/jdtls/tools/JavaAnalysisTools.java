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
 * MCP tools for advanced Java analysis via JDT.LS delegate command handlers.
 * These tools go beyond standard LSP capabilities by leveraging JDT APIs
 * through delegate command handlers running inside JDT.LS.
 */
@ApplicationScoped
public class JavaAnalysisTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_get_type_hierarchy",
          description = "Get the full type hierarchy (supertypes, super interfaces, and subtypes) " +
                        "for a Java type at a specific position in a file, or by fully qualified name. " +
                        "Example: java_get_type_hierarchy(cwd='/project', fileUri='file:///project/src/Main.java', line=5, character=10)")
    public CompletableFuture<String> getTypeHierarchy(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.typeHierarchy",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_get_call_hierarchy_incoming",
          description = "Find all callers of a method at a specific position. " +
                        "Returns the list of methods that call the target method. " +
                        "Example: java_get_call_hierarchy_incoming(cwd='/project', fileUri='file:///project/src/Service.java', line=15, character=10)")
    public CompletableFuture<String> getCallHierarchyIncoming(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.callHierarchyIncoming",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_get_call_hierarchy_outgoing",
          description = "Find all methods called by a method at a specific position. " +
                        "Returns the list of methods that the target method calls. " +
                        "Example: java_get_call_hierarchy_outgoing(cwd='/project', fileUri='file:///project/src/Service.java', line=15, character=10)")
    public CompletableFuture<String> getCallHierarchyOutgoing(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.callHierarchyOutgoing",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_find_annotation_usages",
          description = "Find all usages of a Java annotation type. " +
                        "Returns every location where the annotation is applied (@Annotation). " +
                        "Can search by position in a file or by fully qualified name. " +
                        "Example: java_find_annotation_usages(cwd='/project', fullyQualifiedName='jakarta.inject.Inject')")
    public CompletableFuture<String> findAnnotationUsages(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the annotation (e.g., 'jakarta.inject.Inject')") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.findAnnotationUsages",
                Map.of("fullyQualifiedName", fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_type_instantiations",
          description = "Find all 'new Type()' instantiations of a Java type. " +
                        "Returns every location where the type is instantiated with 'new'. " +
                        "Example: java_find_type_instantiations(cwd='/project', fullyQualifiedName='java.util.ArrayList')")
    public CompletableFuture<String> findTypeInstantiations(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the type (e.g., 'java.util.ArrayList')") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.findTypeInstantiations",
                Map.of("fullyQualifiedName", fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_get_complexity_metrics",
          description = "Compute cyclomatic complexity and lines of code (LOC) per method in a Java file. " +
                        "Returns the complexity score and LOC for each method. " +
                        "Example: java_get_complexity_metrics(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> getComplexityMetrics(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {

        return executor.executeCommand(cwd, "mcp.jdtls.getComplexityMetrics",
                Map.of("uri", fileUri),
                cancellation, progress);
    }
}
