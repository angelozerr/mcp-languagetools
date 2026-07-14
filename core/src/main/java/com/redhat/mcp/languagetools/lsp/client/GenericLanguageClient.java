package com.redhat.mcp.languagetools.lsp.client;

import com.redhat.mcp.languagetools.client.BindEndpointSupport;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generic LSP client implementation with support for capability registration, bindRequest and bindNotification routing.
 * Extends BindEndpointSupport to handle bindRequest and bindNotification routing declared in server.json.
 * Implements Endpoint to handle custom requests.
 *
 * bindRequest: defaults to "executeCommand" mode (workspace/executeCommand)
 * bindNotification: defaults to "direct" mode (direct method call)
 */
public class GenericLanguageClient extends BindEndpointSupport implements LanguageClient, Endpoint {

    private static final Logger LOG = Logger.getLogger(GenericLanguageClient.class);

    protected final LspServer lspServer;

    public GenericLanguageClient(LspServer lspServer) {
        super(lspServer.getConfig(), lspServer.getWorkspace());
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
        LOG.infof("%s message: %s", lspServer.getConfig().getServerId(), messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        LOG.infof("%s log: %s", lspServer.getConfig().getServerId(), message.getMessage());
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        LOG.infof("[%s] Registering capabilities", lspServer.getConfig().getServerId());
        lspServer.getClientFeatures().registerCapability(params);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        LOG.infof("[%s] Unregistering capabilities", lspServer.getConfig().getServerId());
        lspServer.getClientFeatures().unregisterCapability(params);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        List<Object> settings = configurationParams.getItems().stream()
                .map(item -> (Object) null)
                .toList();
        return CompletableFuture.completedFuture(settings);
    }
}
