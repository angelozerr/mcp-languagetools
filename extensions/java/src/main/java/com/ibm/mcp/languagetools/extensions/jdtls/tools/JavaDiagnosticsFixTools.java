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

@ApplicationScoped
public class JavaDiagnosticsFixTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_validate_syntax",
          description = "Quick syntax-only validation of a Java file (no semantic analysis). " +
                        "Faster than full diagnostics - only checks syntax errors. " +
                        "Example: java_validate_syntax(cwd='/project', fileUri='file:///project/src/Main.java')")
    public CompletableFuture<String> validateSyntax(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.validateSyntax",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_get_quick_fixes",
          description = "Get available quick fixes for problems at a specific position in a Java file. " +
                        "Returns a list of fixes that can be applied to resolve the problem. " +
                        "Example: java_get_quick_fixes(cwd='/project', fileUri='file:///project/src/Main.java', line=10, character=5)")
    public CompletableFuture<String> getQuickFixes(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.getQuickFixes",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_apply_quick_fix",
          description = "Apply a specific quick fix to resolve a problem in a Java file. " +
                        "Use java_get_quick_fixes first to get available fixes and their IDs. " +
                        "Example: java_apply_quick_fix(cwd='/project', fileUri='file:///project/src/Main.java', line=10, character=5, fixId='add_import')")
    public CompletableFuture<String> applyQuickFix(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "ID of the fix to apply (from java_get_quick_fixes)") String fixId,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.applyQuickFix",
                Map.of("uri", fileUri, "line", line, "character", character, "fixId", fixId),
                cancellation, progress);
    }

    @Tool(name = "java_diagnose_and_fix",
          description = "Diagnose problems in a Java file and optionally apply safe auto-fixes. " +
                        "Combines diagnostics, quick fix suggestions, and optional auto-fix application. " +
                        "Example: java_diagnose_and_fix(cwd='/project', fileUri='file:///project/src/Main.java')")
    public CompletableFuture<String> diagnoseAndFix(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.diagnoseAndFix",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_apply_cleanup",
          description = "Apply a code cleanup to a Java file. " +
                        "Available cleanups: remove_unused_imports, add_missing_override, convert_to_lambda, " +
                        "remove_unnecessary_casts, add_final_modifier. " +
                        "Example: java_apply_cleanup(cwd='/project', fileUri='file:///project/src/Service.java', cleanupId='remove_unused_imports')")
    public CompletableFuture<String> applyCleanup(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = "Cleanup ID: remove_unused_imports, add_missing_override, convert_to_lambda, remove_unnecessary_casts, add_final_modifier") String cleanupId,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.applyCleanup",
                Map.of("uri", fileUri, "cleanupId", cleanupId),
                cancellation, progress);
    }
}
