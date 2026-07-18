package com.ibm.mcp.languagetools.extensions.jdtls.lsp;

import com.ibm.mcp.languagetools.lsp.client.GenericLanguageClient;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.jboss.logging.Logger;

/**
 * Language client for JDT.LS with support for java/languageStatus notifications.
 * Extends GenericLanguageClient to inherit bindRequest routing.
 */
public class JdtLsLanguageClient extends GenericLanguageClient {

    private static final Logger LOG = Logger.getLogger(JdtLsLanguageClient.class);

    private final JdtLsServer server;
    private volatile String currentStatus = "Starting";

    public JdtLsLanguageClient(JdtLsServer server) {
        super(server);
        this.server = server;
    }

    @JsonNotification("language/status")
    public void languageStatus(StatusReport status) {
        LOG.infof("JDT.LS status [%s]: %s", status.getType(), status.getMessage());
        currentStatus = status.getMessage();

        server.setStatusMessage(status.getMessage());

        if ("ServiceReady".equals(status.getType()) ||
            (status.getMessage() != null && status.getMessage().contains("Ready"))) {
            LOG.info("JDT.LS is ready!");
            server.onServiceReady();
        }
    }

    public String getCurrentStatus() {
        return currentStatus;
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
