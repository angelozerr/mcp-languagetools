package com.redhat.mcp.languagetools.lsp.trace;

import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.trace.TracingMessageConsumer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Collects LSP trace messages from all LSP servers.
 * Keeps a bounded buffer of recent messages and fires CDI events for real-time streaming.
 */
@ApplicationScoped
@RegisterForReflection
public class LspTraceCollector implements TracingMessageConsumer.TraceCollectorAdd {

    private static final Logger LOG = Logger.getLogger(LspTraceCollector.class);
    private static final int MAX_TRACE_MESSAGES = 1000;

    private final ConcurrentLinkedDeque<LspTraceMessage> traces = new ConcurrentLinkedDeque<>();

    @Inject
    Event<LspTraceMessage> traceEvent;

    @Override
    public void trace(String message, TraceCollector.MessageType type) {
        // Default implementation - subclasses can override if needed
        // For LSP, traces should go through addTrace() with workspace context
    }

    @Override
    public void addTrace(String workspaceUri, String serverId, String jsonContent) {
        addTrace(workspaceUri, serverId, jsonContent, TraceCollector.MessageType.TRACE);
    }

    public void addTrace(String workspaceUri,
                         String serverId,
                         String jsonContent,
                         TraceCollector.MessageType messageType) {
        LspTraceMessage message = new LspTraceMessage(
            workspaceUri,
            serverId,
            Instant.now(),
            jsonContent,
            messageType
        );

        traces.addLast(message);

        // Trim to max size
        while (traces.size() > MAX_TRACE_MESSAGES) {
            traces.pollFirst();
        }

        // Fire CDI event for real-time streaming
        traceEvent.fire(message);
    }

    public List<LspTraceMessage> getTracesForWorkspaceAndServer(String workspaceUri, String serverId, int limit) {
        return traces.stream()
            .filter(t -> t.workspaceUri().equals(workspaceUri) && t.serverId().equals(serverId))
            .skip(Math.max(0, traces.size() - limit))
            .toList();
    }

    public void clear() {
        traces.clear();
    }
}
