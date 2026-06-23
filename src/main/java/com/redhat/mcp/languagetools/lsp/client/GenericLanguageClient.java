package com.redhat.mcp.languagetools.lsp.client;

import com.redhat.mcp.languagetools.lsp.server.LspServer;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Generic LSP client implementation.
 */
public class GenericLanguageClient implements LanguageClient {

    private static final Logger LOG = Logger.getLogger(GenericLanguageClient.class);

    private final LspServer lspServer;

    public GenericLanguageClient(LspServer lspServer) {
        this.lspServer = lspServer;
    }

    @Override
    public void telemetryEvent(Object object) {
        // Ignore telemetry for now
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        LOG.debugf("Diagnostics published for: %s", diagnostics.getUri());
        lspServer.getDiagnosticsCache().put(diagnostics.getUri(), diagnostics.getDiagnostics());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        LOG.infof("%s message: %s", lspServer.getConfig().getId(), messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        LOG.infof("%s log: %s", lspServer.getConfig().getId(), message.getMessage());
    }
}
