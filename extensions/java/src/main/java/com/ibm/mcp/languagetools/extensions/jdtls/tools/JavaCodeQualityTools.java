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
 * MCP tools for Java code quality analysis via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaCodeQualityTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_find_large_classes",
          description = "Find Java classes exceeding size thresholds (methods, fields, lines of code). " +
                        "Example: java_find_large_classes(cwd='/project')")
    public CompletableFuture<String> findLargeClasses(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_LARGE_CLASSES,
                Map.of(),
                cancellation, progress);
    }

    @Tool(name = "java_find_naming_violations",
          description = "Find naming convention violations in a Java file. " +
                        "Checks CamelCase for types, camelCase for methods/fields, UPPER_SNAKE for constants. " +
                        "Example: java_find_naming_violations(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> findNamingViolations(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.FIND_NAMING_VIOLATIONS, uris,
                    uri -> Map.of("uri", uri), cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.FIND_NAMING_VIOLATIONS,
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_find_possible_bugs",
          description = "Find potential bug patterns in a Java file. " +
                        "Detects: empty catch blocks, String == comparison, equals without hashCode, resource leaks, etc. " +
                        "Example: java_find_possible_bugs(cwd='/project', fileUri='file:///project/src/Service.java')")
    public CompletableFuture<String> findPossibleBugs(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.FIND_POSSIBLE_BUGS, uris,
                    uri -> Map.of("uri", uri), cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.FIND_POSSIBLE_BUGS,
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_find_circular_dependencies",
          description = "Find circular package dependencies in a Java project. " +
                        "Returns cycles in the package dependency graph. " +
                        "Example: java_find_circular_dependencies(cwd='/project')")
    public CompletableFuture<String> findCircularDependencies(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.FIND_CIRCULAR_DEPENDENCIES,
                Map.of(),
                cancellation, progress);
    }
}
