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

import com.ibm.mcp.languagetools.Application;
import com.ibm.mcp.languagetools.lsp.server.LspServer;
import com.ibm.mcp.languagetools.progress.ProgressMonitorManager;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Executor for JDT.LS delegate commands.
 * Resolves the JDT.LS server for a workspace and sends executeCommand requests.
 */
@ApplicationScoped
public class JdtlsCommandExecutor {

    private static final Logger LOG = Logger.getLogger(JdtlsCommandExecutor.class);

    private static final String JDTLS_SERVER_ID = "jdtls";

    @Inject
    Application application;

    @Inject
    ProgressMonitorManager progressMonitorManager;

    /**
     * Execute a JDT.LS delegate command.
     *
     * @param cwd          current working directory
     * @param commandId    the command ID (e.g., "mcp.jdtls.typeHierarchy")
     * @param arguments    command arguments
     * @param cancellation cancellation token
     * @param progress     progress reporting
     * @return the command result as a string
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<String> executeCommand(String cwd, String commandId, Object arguments,
                                                     Cancellation cancellation, Progress progress) {
        return application.getWorkspaceForPath(cwd)
                .thenCompose(workspace -> {
                    LspServer jdtls = workspace.getLspServers().stream()
                            .filter(s -> JDTLS_SERVER_ID.equals(s.getConfig().getServerId()))
                            .findFirst()
                            .orElse(null);

                    if (jdtls == null) {
                        return CompletableFuture.completedFuture(
                                "Error: JDT.LS server not found for workspace " + cwd);
                    }

                    return jdtls.waitUntilReady()
                            .thenCompose(v -> {
                                List<Object> args = arguments instanceof List
                                        ? (List<Object>) arguments
                                        : List.of(arguments);
                                return jdtls.executeCommand(commandId, args);
                            })
                            .thenApply(this::formatResult)
                            .exceptionally(ex -> {
                                LOG.errorf(ex, "Failed to execute command %s", commandId);
                                return "Error executing " + commandId + ": " + ex.getMessage();
                            });
                });
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "No result";
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
}
