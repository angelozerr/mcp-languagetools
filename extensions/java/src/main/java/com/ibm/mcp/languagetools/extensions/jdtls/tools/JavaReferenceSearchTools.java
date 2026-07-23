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
 * MCP tools for Java reference search via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaReferenceSearchTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_find_casts",
          description = "Find all cast expressions to a Java type")
    public CompletableFuture<String> findCasts(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the type (e.g., 'java.lang.String')") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_CASTS,
                RefactoringHelper.fqnParams(fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_catch_blocks",
          description = "Find all catch blocks catching a Java exception type")
    public CompletableFuture<String> findCatchBlocks(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the exception type") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_CATCH_BLOCKS,
                RefactoringHelper.fqnParams(fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_instanceof_checks",
          description = "Find all instanceof checks for a Java type")
    public CompletableFuture<String> findInstanceofChecks(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the type") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_INSTANCEOF_CHECKS,
                RefactoringHelper.fqnParams(fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_throws_declarations",
          description = "Find all throws clause declarations of a Java exception type")
    public CompletableFuture<String> findThrowsDeclarations(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the exception type") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_THROWS_DECLARATIONS,
                RefactoringHelper.fqnParams(fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_type_arguments",
          description = "Find all type argument usages of a Java type in generics")
    public CompletableFuture<String> findTypeArguments(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = "Fully qualified name of the type") String fullyQualifiedName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_TYPE_ARGUMENTS,
                RefactoringHelper.fqnParams(fullyQualifiedName),
                cancellation, progress);
    }

    @Tool(name = "java_find_method_references",
          description = "Find all references to a method")
    public CompletableFuture<String> findMethodReferences(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = JavaToolArgDescriptions.SEARCH_SCOPE, required = false) String scope,
            @ToolArg(description = JavaToolArgDescriptions.PROJECT_NAME, required = false) String projectName,
            Cancellation cancellation,
            Progress progress) {
        Map<String, Object> params = RefactoringHelper.positionParams(fileUri, line, character);
        RefactoringHelper.putScope(params, scope, projectName);
        return executor.executeCommand(cwd, JdtlsCommands.FIND_METHOD_REFERENCES, params, cancellation, progress);
    }
}
