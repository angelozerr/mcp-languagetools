package com.ibm.mcp.languagetools.trace;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record TraceMessage(
        TraceKind kind,
        String workspaceUri,
        String contextId,
        Instant timestamp,
        String content,
        TraceCollector.MessageType messageType
) {
}
