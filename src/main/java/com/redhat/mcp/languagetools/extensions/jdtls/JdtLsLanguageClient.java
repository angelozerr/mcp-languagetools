package com.redhat.mcp.languagetools.extensions.jdtls;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Language client for JDT.LS with support for java/languageStatus notifications.
 */
public class JdtLsLanguageClient implements LanguageClient {

    private static final Logger LOG = Logger.getLogger(JdtLsLanguageClient.class);

    private final JdtLsServer server;
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private volatile String currentStatus = "Starting";

    public JdtLsLanguageClient(JdtLsServer server) {
        this.server = server;
    }

    /**
     * JDT.LS-specific status notification.
     * This is called by JDT.LS to report its status (e.g., "Starting", "Ready").
     */
    @JsonNotification("language/status")
    public void languageStatus(StatusReport status) {
        LOG.infof("JDT.LS status [%s]: %s", status.getType(), status.getMessage());
        currentStatus = status.getMessage();

        // JDT.LS reports "ServiceReady" when it's done indexing and ready
        if ("ServiceReady".equals(status.getType()) ||
            (status.getMessage() != null && status.getMessage().contains("Ready"))) {
            LOG.info("JDT.LS is ready!");
            server.setReady(true);
            readyLatch.countDown();
        }
    }

    /**
     * Wait for JDT.LS to become ready.
     * @param timeoutSeconds timeout in seconds
     * @return true if ready, false if timed out
     */
    public boolean awaitReady(long timeoutSeconds) {
        try {
            return readyLatch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    @Override
    public void telemetryEvent(Object object) {
        // Ignore telemetry for now
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        LOG.debugf("Diagnostics published for: %s", diagnostics.getUri());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        LOG.infof("JDT.LS message: %s", messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        LOG.infof("JDT.LS log: %s", message.getMessage());
    }

    /**
     * StatusReport for language/status notification.
     */
    public static class StatusReport {
        private String type;
        private String message;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
