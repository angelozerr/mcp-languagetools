package com.redhat.mcp.languagetools.dap.trace;

import com.redhat.mcp.languagetools.trace.TraceCollector;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * Represents a single DAP trace message (request, response, or event).
 */
@RegisterForReflection
public record DapTraceMessage(
    String workspaceUri,
    String sessionId,
    Instant timestamp,
    TraceCollector.MessageDirection direction,
    String jsonContent,
    TraceCollector.MessageType messageType  // null = TRACE (default)
) {}
