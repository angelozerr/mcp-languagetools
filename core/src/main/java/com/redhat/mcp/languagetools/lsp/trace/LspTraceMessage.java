package com.redhat.mcp.languagetools.lsp.trace;

import com.redhat.mcp.languagetools.trace.TraceCollector;

import java.time.Instant;

/**
 * Represents a captured LSP trace message (request/response/notification).
 * Similar to lsp4ij TracingMessageConsumer format.
 */
public record LspTraceMessage(
    String workspaceUri,
    String serverId,
    Instant timestamp,
    String jsonContent,
    TraceCollector.MessageType messageType  // null = TRACE (default)
) {
}
