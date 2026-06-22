package com.redhat.mcp.languagetools.lsp;

import java.net.URI;

/**
 * CDI event fired when an LSP server status changes.
 */
public record LspServerStatusChangeEvent(
    URI workspaceUri,
    String serverId,
    ServerStatus oldStatus,
    ServerStatus newStatus
) {
}
