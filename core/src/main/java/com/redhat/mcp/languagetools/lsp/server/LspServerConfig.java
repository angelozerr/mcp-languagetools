package com.redhat.mcp.languagetools.lsp.server;

import com.redhat.mcp.languagetools.Application;
import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.server.ServerConfigBase;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a language server.
 * Can be loaded from JSON or built programmatically.
 */
public class LspServerConfig extends ServerConfigBase {

    /**
     * Server initialization options
     */
    private Map<String, Object> initializationOptions = new HashMap<>();

    /**
     * Whether to skip sending didOpen before position-based requests (references, definition, etc.).
     * Defaults to false (didOpen is sent). Set to true for servers that index the whole project (e.g. JDTLS, pyright).
     */
    private boolean skipDidOpen;

    public LspServerConfig(String serverId, Application application) {
        super(serverId, application.getPathManager().getLspServerHome(serverId), application);
    }

    /**
     * Detect parent server ID from contributes configuration.
     * For contribution-only configs (like Quarkus), the parent is the server
     * they contribute classpath JARs to (e.g., microprofile).
     *
     * @return parent server ID, or null if no parent
     */
    public String getParentServerId() {
        var contributes = getContributes();
        if (contributes == null || contributes.getContributions() == null || contributes.getContributions().isEmpty()) {
            return null;
        }

        // Find the contribution with classpath - that's the parent server
        return contributes.getContributions().entrySet().stream()
            .filter(entry -> {
                var contribution = entry.getValue();
                if (!contribution.isJsonObject()) {
                    return false;
                }
                var obj = contribution.getAsJsonObject();
                return obj.has(ClasspathExtensibleContributes.CLASSPATH)
                    && obj.get(ClasspathExtensibleContributes.CLASSPATH).isJsonArray();
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    // Getters and setters (id, name, description, command, env, workingDirectory, installer inherited from ServerConfigBase)

    public Map<String, Object> getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(Map<String, Object> initializationOptions) {
        this.initializationOptions = initializationOptions;
    }

    public boolean isSkipDidOpen(LspCapability capability) {
        return skipDidOpen;
    }

    public void setSkipDidOpen(boolean skipDidOpen) {
        this.skipDidOpen = skipDidOpen;
    }

    @Override
    public String toString() {
        return "LspServerConfig{" +
                "id='" + getServerId() + '\'' +
                ", name='" + name + '\'' +
                ", command='" + getCommand() + '\'' +
                ", documentSelector=" + getDocumentSelector() +
                '}';
    }

}
