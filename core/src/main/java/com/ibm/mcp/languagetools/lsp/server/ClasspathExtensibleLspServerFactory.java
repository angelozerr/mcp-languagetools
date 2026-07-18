package com.ibm.mcp.languagetools.lsp.server;

import com.ibm.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

/**
 * Factory for creating ClasspathExtensibleLspServer instances.
 * Handles servers that declare "acceptContributions": ["classpath"] in their server.json.
 */
public class ClasspathExtensibleLspServerFactory implements LspServerFactory {

    private static final Logger LOG = Logger.getLogger(ClasspathExtensibleLspServerFactory.class);

    @Override
    public boolean canHandle(LspServerConfig config, Workspace workspace) {
        if (config == null || config.isContributionOnly()) {
            return false;
        }
        return config.acceptsContribution(ClasspathExtensibleContributes.CLASSPATH);
    }

    @Override
    public LspServer createServer(LspServerCreateParams params) {
        LOG.infof("Creating classpath-extensible LSP server for %s", params.getConfig().getServerId());
        return new ClasspathExtensibleLspServer(params.getConfig(), params.getWorkspace());
    }
}
