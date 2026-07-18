package com.ibm.mcp.languagetools.lsp.server;

import com.ibm.mcp.languagetools.workspace.Workspace;
import org.jboss.logging.Logger;

/**
 * Factory for creating ClasspathExtensibleLspServer instances.
 * Handles servers that have contributions with classpath extensions.
 */
public class ClasspathExtensibleLspServerFactory implements LspServerFactory {

    private static final Logger LOG = Logger.getLogger(ClasspathExtensibleLspServerFactory.class);

    @Override
    public boolean canHandle(LspServerConfig config, Workspace workspace) {
        // This factory handles servers that receive classpath contributions
        if (config == null || config.isContributionOnly() || config.getCommand() == null) {
            return false;
        }

        String serverId = config.getServerId();

        // Check all LSP server configs to see if any contributes classpath to this serverId
        return workspace.getApplication().getLspServerConfigs().stream()
            .filter(otherConfig -> !otherConfig.getServerId().equals(serverId))
            .anyMatch(otherConfig -> {
                var contributes = otherConfig.getContributes();
                if (contributes == null || contributes.getContributions() == null) {
                    return false;
                }

                var contribution = contributes.getContribution(serverId);
                if (contribution == null || !contribution.isJsonObject()) {
                    return false;
                }

                var obj = contribution.getAsJsonObject();
                return obj.has(ClasspathExtensibleContributes.CLASSPATH)
                    && obj.get(ClasspathExtensibleContributes.CLASSPATH).isJsonArray();
            });
    }

    @Override
    public LspServer createServer(LspServerCreateParams params) {
        LOG.infof("Creating classpath-extensible LSP server for %s", params.getConfig().getServerId());
        return new ClasspathExtensibleLspServer(params.getConfig(), params.getWorkspace());
    }
}
