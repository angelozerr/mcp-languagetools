package com.redhat.mcp.languagetools.lsp.tools.strategies;

import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.tools.LspRequestExecutor;
import com.redhat.mcp.languagetools.lsp.tools.params.FileUriRequestParams;
import org.eclipse.lsp4j.*;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Base strategy for tools that need file diagnostics.
 * Calls server.getDiagnostics(uri, languageId, autoClose) which handles
 * custom requests (e.g. MicroProfileLspServer) or didOpen fallback.
 *
 * @param <TRequestParams> Tool request params (must extend FileUriRequestParams)
 * @param <TLspParams>     LSP params type
 * @param <TResult>        Result type
 */
public abstract class DidOpenBasedStrategy<TRequestParams extends FileUriRequestParams, TLspParams, TResult>
        implements LspRequestExecutor.LspRequestStrategy<TRequestParams, TLspParams, TResult> {

    private static final Logger LOG = Logger.getLogger(DidOpenBasedStrategy.class);

    protected final LanguageRegistry languageRegistry;

    protected DidOpenBasedStrategy(LanguageRegistry languageRegistry) {
        this.languageRegistry = languageRegistry;
    }

    /**
     * Extract the file URI from the LSP params.
     */
    protected abstract String extractFileUri(TLspParams lspParams);

    /**
     * Execute the tool logic after diagnostics are available in the server cache.
     */
    protected abstract CompletableFuture<TResult> executeAfterDiagnostics(LspServer server, TLspParams lspParams);

    /**
     * Whether to auto-close the file after getting diagnostics.
     * Override to return false when the file must stay open (e.g. code actions).
     */
    protected boolean autoClose() {
        return true;
    }

    @Override
    public final CompletableFuture<TResult> executeRequest(LspServer server, TLspParams lspParams) {
        String fileUri = extractFileUri(lspParams);
        String languageId = languageRegistry.detectLanguage(URI.create(fileUri)).orElse("");

        return server.getDiagnostics(fileUri, languageId, autoClose())
                .thenCompose(diags -> executeAfterDiagnostics(server, lspParams))
                .whenComplete((result, ex) -> {
                    if (!autoClose() && server.isFileOpened(fileUri)) {
                        try {
                            DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams();
                            closeParams.setTextDocument(new TextDocumentIdentifier(fileUri));
                            server.getLanguageServer().getTextDocumentService().didClose(closeParams);
                            server.markFileClosed(fileUri);
                        } catch (Exception e) {
                            LOG.warnf(e, "Failed to send didClose for %s", fileUri);
                        }
                    }
                });
    }
}
