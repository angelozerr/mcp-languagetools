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

import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerResolver;
import com.redhat.mcp.languagetools.lsp.tools.params.LspRequestParams;
import com.redhat.mcp.languagetools.progress.McpProgressMonitor;
import com.redhat.mcp.languagetools.progress.MultiProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressContext;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressMonitorContributor;
import com.redhat.mcp.languagetools.progress.ProgressStep;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generic executor for LSP requests.
 * Handles the common pattern:
 * 1. Resolve LSP servers (via strategy)
 * 2. Execute LSP request on all servers in parallel
 * 3. Merge and format results
 *
 * @param <TRequestParams> Request parameters type (e.g., PositionBasedRequestParams, WorkspaceSymbolRequestParams)
 * @param <TLspParams>     LSP request parameters type (e.g., ReferenceParams, DefinitionParams)
 * @param <TResult>        LSP response type (e.g., List<Location>, List<LocationLink>)
 */
@ApplicationScoped
public class LspRequestExecutor {

    private static final Logger LOG = Logger.getLogger(LspRequestExecutor.class);

    @Inject
    LspServerResolver serverResolver;

    @Inject
    Instance<ProgressMonitorContributor> progressContributors;

    /**
     * Execute an LSP request across all capable servers.
     *
     * @param params       Request parameters
     * @param strategy     Strategy for resolving servers, building params, executing request, and formatting results
     * @param cancellation Cancellation token
     * @param progress
     * @return Formatted result as String
     */
    public <TRequestParams extends LspRequestParams, TLspParams, TResult> CompletableFuture<String> execute(
            TRequestParams params,
            LspRequestStrategy<TRequestParams, TLspParams, TResult> strategy,
            Cancellation cancellation,
            Progress progress) {
        // Create MCP progress monitor
        McpProgressMonitor mcpMonitor = new McpProgressMonitor(progress, cancellation);

        // Collect additional monitors from contributors (e.g., Admin module)
        List<ProgressMonitor> monitors = new ArrayList<>();
        monitors.add(mcpMonitor);

        ProgressContext context = ProgressContext.forOperation(null, strategy.getCapability().name());
        for (ProgressMonitorContributor contributor : progressContributors) {
            ProgressMonitor contributed = contributor.createMonitor(context);
            if (contributed != null && contributed != ProgressMonitor.none()) {
                monitors.add(contributed);
            }
        }

        // Use MultiProgressMonitor if we have multiple monitors
        ProgressMonitor progressMonitor = monitors.size() > 1
                ? new MultiProgressMonitor(monitors.toArray(new ProgressMonitor[0]))
                : mcpMonitor;

        // Define steps for LSP operations
        progressMonitor.addStep(ProgressStep.INSTALLING, 0.40);
        progressMonitor.addStep(ProgressStep.STARTING, 0.10);
        progressMonitor.addStep(ProgressStep.INDEXING, 0.35);
        progressMonitor.addStep(ProgressStep.EXECUTING, 0.15);

        // Initialize steps for WebSocket monitors (broadcast step definitions)
        if (progressMonitor instanceof com.redhat.mcp.languagetools.progress.MultiProgressMonitor multiMonitor) {
            for (ProgressMonitor monitor : multiMonitor.getDelegates()) {
                if (monitor instanceof com.redhat.mcp.languagetools.admin.WebSocketProgressMonitor wsMonitor) {
                    wsMonitor.initializeSteps();
                }
            }
        } else if (progressMonitor instanceof com.redhat.mcp.languagetools.admin.WebSocketProgressMonitor wsMonitor) {
            wsMonitor.initializeSteps();
        }

        ProgressMonitor installMonitor = progressMonitor.beginStep(ProgressStep.INSTALLING);
        installMonitor.reportProgress(0.0, "Installing language server");

        return strategy.resolveServers(serverResolver, params, installMonitor)
                .thenCompose(servers -> {
                    if (servers.isEmpty()) {
                        progressMonitor.setComplete();
                        return CompletableFuture.completedFuture(
                                strategy.formatNoServerFound(params)
                        );
                    }

                    // Build server names for progress messages
                    String serverNames = servers.stream()
                            .map(s -> s.getConfig().getName())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");

                    progressMonitor.beginStep(ProgressStep.STARTING);
                    ProgressMonitor indexingMonitor = progressMonitor.beginStep(ProgressStep.INDEXING);
                    indexingMonitor.reportProgress(0.0, "Indexing " + serverNames);

                    // Build LSP request parameters
                    TLspParams lspParams = strategy.buildLspParams(params);

                    // Execute LSP request on all servers in parallel (wait for ready first)
                    List<CompletableFuture<TResult>> futures = servers.stream()
                            .map(server -> server.waitUntilReady()
                                    .thenApply(v -> {
                                        // Server ready - complete indexing step
                                        indexingMonitor.setComplete();

                                        ProgressMonitor execMonitor = progressMonitor.beginStep(ProgressStep.EXECUTING);
                                        execMonitor.reportProgress(0.0, "Executing " + strategy.getCapability().name().toLowerCase());
                                        return execMonitor;
                                    })
                                    .thenCompose(execMonitor ->
                                            // Execute request
                                            progressMonitor.executeWithCancellation(strategy.executeRequest(server, lspParams))
                                                    .thenApply(result -> {
                                                        execMonitor.setComplete();
                                                        return result;
                                                    }))
                                    .exceptionally(ex -> {
                                        LOG.warnf("Failed to execute %s on server %s: %s",
                                                strategy.getCapability(), server.getConfig().getServerId(), ex.getMessage());
                                        return strategy.getEmptyResult();
                                    }))
                            .toList();






                    // Step 3: Wait for all to complete and merge results
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                List<TResult> results = futures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(strategy::isValidResult)
                                        .toList();

                                if (results.isEmpty()) {
                                    progressMonitor.setComplete();
                                    return strategy.formatNoResultFound(params);
                                }

                                // Format and return results
                                String formatted = strategy.formatResults(params, results);

                                // Mark complete
                                progressMonitor.setComplete();

                                return formatted;
                            });
                }).exceptionally(ex -> {
                    LOG.error("Failed to execute LSP request", ex);
                    return strategy.formatError(params, ex);
                });
    }

    /**
     * Strategy interface for customizing LSP request execution.
     *
     * @param <TRequestParams> Request parameters type
     * @param <TLspParams>     LSP request parameters type
     * @param <TResult>        LSP response type
     */
    public interface LspRequestStrategy<TRequestParams extends LspRequestParams, TLspParams, TResult> {

        /**
         * Get the LSP capability for this request.
         */
        LspCapability getCapability();

        /**
         * Resolve LSP servers for this request.
         * Different requests resolve servers differently (file-based vs workspace-based).
         */
        CompletableFuture<List<LspServer>> resolveServers(
                LspServerResolver resolver,
                TRequestParams params, ProgressMonitor progressMonitor);

        /**
         * Build LSP request parameters from request params.
         */
        TLspParams buildLspParams(TRequestParams params);

        /**
         * Execute the LSP request on a server.
         */
        CompletableFuture<TResult> executeRequest(LspServer server, TLspParams lspParams);

        /**
         * Get empty result (used for failed requests).
         */
        TResult getEmptyResult();

        /**
         * Check if a result is valid (non-null, non-empty).
         */
        boolean isValidResult(TResult result);

        /**
         * Format results into a human-readable string.
         */
        String formatResults(TRequestParams params, List<TResult> results);

        /**
         * Format error message when no server is found.
         */
        default String formatNoServerFound(TRequestParams params) {
            return String.format("No language server with %s support found", getCapability());
        }

        /**
         * Format message when no results are found.
         */
        String formatNoResultFound(TRequestParams params);

        /**
         * Format error message.
         */
        default String formatError(TRequestParams params, Throwable ex) {
            return "Failed to execute request: " + ex.getMessage();
        }
    }
}

