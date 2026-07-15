package com.redhat.mcp.languagetools.lsp.tools.strategies;

import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.tools.LspRequestExecutor;
import com.redhat.mcp.languagetools.lsp.tools.params.FileUriRequestParams;
import org.eclipse.lsp4j.*;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base strategy for tools that need file diagnostics (via didOpen or custom request).
 * Handles two paths per server:
 * 1. Custom request via toolRequests config (fast, synchronous via bindRequest)
 * 2. Fallback: didOpen + waitForDiagnostics + executeAfterDidOpen + didClose
 *
 * @param <TRequestParams> Tool request params (must extend FileUriRequestParams)
 * @param <TLspParams>     LSP params type
 * @param <TResult>        Result type
 */
public abstract class DidOpenBasedStrategy<TRequestParams extends FileUriRequestParams, TLspParams, TResult>
        implements LspRequestExecutor.LspRequestStrategy<TRequestParams, TLspParams, TResult> {

    private static final Logger LOG = Logger.getLogger(DidOpenBasedStrategy.class);

    private static final long DIAGNOSTICS_TIMEOUT_MS = 10_000;

    protected final LanguageRegistry languageRegistry;

    protected DidOpenBasedStrategy(LanguageRegistry languageRegistry) {
        this.languageRegistry = languageRegistry;
    }

    /**
     * The tool name used to lookup custom requests in server.json toolRequests section.
     */
    protected abstract String getToolRequestName();

    /**
     * Extract the file URI from the LSP params.
     */
    protected abstract String extractFileUri(TLspParams lspParams);

    /**
     * Build the parameters for the custom request by calling the appropriate
     * typed method on the server (e.g., server.buildDiagnosticsRequestParams).
     */
    protected abstract Object buildCustomRequestParams(LspServer server, String fileUri, TLspParams lspParams);

    /**
     * Parse the raw result from a custom request into the expected type
     * by calling the appropriate typed method on the server.
     */
    protected abstract TResult parseCustomRequestResult(LspServer server, Object result);

    /**
     * Execute the standard approach after didOpen + diagnostics are available.
     * At this point the file is opened and the diagnostics cache is populated.
     */
    protected abstract CompletableFuture<TResult> executeAfterDidOpen(LspServer server, TLspParams lspParams);

    @Override
    public final CompletableFuture<TResult> executeRequest(LspServer server, TLspParams lspParams) {
        String fileUri = extractFileUri(lspParams);
        String customRequest = server.getConfig().getToolRequest(getToolRequestName());
        if (customRequest != null) {
            return executeViaCustomRequest(server, customRequest, fileUri, lspParams);
        }
        return executeWithDidOpen(server, lspParams, fileUri);
    }

    private CompletableFuture<TResult> executeViaCustomRequest(
            LspServer server, String method, String fileUri, TLspParams lspParams) {
        LOG.infof("Using custom request '%s' on %s (server: %s)",
                method, fileUri, server.getConfig().getServerId());

        var client = server.getLanguageClient();
        if (client == null) {
            return CompletableFuture.completedFuture(getEmptyResult());
        }

        CompletableFuture<?> rawFuture = client.request(method, buildCustomRequestParams(server, fileUri, lspParams));
        return rawFuture
                .thenApply(result -> parseCustomRequestResult(server, result))
                .exceptionally(ex -> {
                    LOG.warnf(ex, "Custom request '%s' failed for %s", method, fileUri);
                    return getEmptyResult();
                });
    }

    private CompletableFuture<TResult> executeWithDidOpen(LspServer server, TLspParams lspParams, String fileUri) {
        LOG.infof("Using didOpen fallback on %s (server: %s)",
                fileUri, server.getConfig().getServerId());

        if (server.isFileOpened(fileUri)) {
            return executeAfterDidOpen(server, lspParams);
        }

        String content;
        try {
            content = Files.readString(Paths.get(URI.create(fileUri)));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to read file %s for didOpen", fileUri);
            return CompletableFuture.completedFuture(getEmptyResult());
        }

        String languageId = languageRegistry.detectLanguage(URI.create(fileUri)).orElse("");

        CompletableFuture<List<Diagnostic>> diagnosticsFuture =
                server.waitForDiagnostics(fileUri, DIAGNOSTICS_TIMEOUT_MS);

        DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams();
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(fileUri);
        textDocument.setLanguageId(languageId);
        textDocument.setVersion(1);
        textDocument.setText(content);
        openParams.setTextDocument(textDocument);

        server.getLanguageServer().getTextDocumentService().didOpen(openParams);
        server.markFileOpened(fileUri);

        return diagnosticsFuture
                .thenCompose(diags -> executeAfterDidOpen(server, lspParams))
                .whenComplete((result, ex) -> {
                    try {
                        DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams();
                        closeParams.setTextDocument(new TextDocumentIdentifier(fileUri));
                        server.getLanguageServer().getTextDocumentService().didClose(closeParams);
                        server.markFileClosed(fileUri);
                    } catch (Exception e) {
                        LOG.warnf(e, "Failed to send didClose for %s", fileUri);
                    }
                });
    }
}
