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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.ibm.mcp.languagetools.extensions.jdtls.tools.RefactoringHelper.positionParams;

/**
 * MCP tools for Java diagnostics and quick fixes via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
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
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.VALIDATE_SYNTAX, uris,
                    uri -> Map.of("uri", uri), cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.VALIDATE_SYNTAX,
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_get_quick_fixes",
          description = "Get available quick fixes for problems at a specific position in a Java file. " +
                        "Returns a list of fixes that can be applied to resolve the problem. " +
                        "Use java_get_quick_fixes first to get available fixes and their IDs. " +
                        "Example: java_get_quick_fixes(cwd='/project', fileUri='file:///project/src/Main.java', line=10, character=5)")
    public CompletableFuture<String> getQuickFixes(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "ID of the fix to apply (from a previous call without fixId)", required = false) String fixId,
            @ToolArg(description = ToolArgDescriptions.APPLY, required = false) Boolean apply,
            Cancellation cancellation,
            Progress progress) {
        Map<String, Object> params = positionParams(fileUri, line, character);
        if (fixId != null) {
            params.put("fixId", fixId);
            RefactoringHelper.putApply(params, apply);
            return executor.executeCommand(cwd, JdtlsCommands.APPLY_QUICK_FIX, params, cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.GET_QUICK_FIXES, params, cancellation, progress);
    }

    @Tool(name = "java_diagnose_and_fix",
          description = "Diagnose problems in a Java file and optionally apply safe auto-fixes. " +
                        "Combines diagnostics, quick fix suggestions, and optional auto-fix application. " +
                        "Example: java_diagnose_and_fix(cwd='/project', fileUri='file:///project/src/Main.java')")
    public CompletableFuture<String> diagnoseAndFix(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.DIAGNOSE_AND_FIX, uris,
                    uri -> Map.of("uri", uri), cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.DIAGNOSE_AND_FIX,
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
            @ToolArg(description = JavaToolArgDescriptions.FILE_URIS, required = false) List<String> fileUris,
            Cancellation cancellation,
            Progress progress) {
        List<String> uris = RefactoringHelper.resolveFileUris(fileUri, fileUris);
        if (uris.size() > 1) {
            return executor.executeBatchCommand(cwd, JdtlsCommands.APPLY_CLEANUP, uris,
                    uri -> {
                        Map<String, Object> params = new HashMap<>();
                        params.put("uri", uri);
                        params.put("cleanupId", cleanupId);
                        return params;
                    }, cancellation, progress);
        }
        return executor.executeCommand(cwd, JdtlsCommands.APPLY_CLEANUP,
                Map.of("uri", fileUri, "cleanupId", cleanupId),
                cancellation, progress);
    }
}
