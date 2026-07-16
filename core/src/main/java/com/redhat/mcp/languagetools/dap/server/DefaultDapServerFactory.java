package com.redhat.mcp.languagetools.dap.server;

import com.redhat.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

/**
 * Default fallback factory for creating DapServer instances.
 * This factory is NOT registered via SPI - it's used as a last resort
 * when no other factory can handle the configuration.
 */
public class DefaultDapServerFactory implements DapServerFactory {

    private static final Logger LOG = Logger.getLogger(DefaultDapServerFactory.class);

    public boolean canHandle(DapServerConfig config, Workspace workspace) {
        // This factory handles everything as a fallback
        return true;
    }

    @Override
    public DapServer createServer(DapServerCreateParams params) {
        LOG.infof("Creating default DAP server for %s (session: %s)", params.getConfig().getServerId(), params.getSession().getSessionId());
        return new DapServer(params.getSession(), params.getConfig(), params.getWorkspace());
    }
}
