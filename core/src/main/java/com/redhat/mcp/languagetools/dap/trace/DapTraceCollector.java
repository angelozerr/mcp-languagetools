package com.redhat.mcp.languagetools.dap.trace;

import com.redhat.mcp.languagetools.trace.TracingMessageConsumer;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Collects DAP trace messages from all debug sessions.
 * Keeps a bounded buffer of recent messages and fires CDI events for real-time streaming.
 */
@ApplicationScoped
@RegisterForReflection
public class DapTraceCollector implements TracingMessageConsumer.TraceCollectorAdd {

    private static final Logger LOG = Logger.getLogger(DapTraceCollector.class);
    private static final int MAX_TRACE_MESSAGES = 1000;

    private final ConcurrentLinkedDeque<DapTraceMessage> traces = new ConcurrentLinkedDeque<>();

    @Inject
    Event<DapTraceMessage> traceEvent;

    @Override
    public void trace(String message, TraceCollector.MessageType type) {
        // Default implementation - subclasses can override if needed
        // For DAP, traces should go through addTrace() with session context
    }

    @Override
    public void addTrace(String workspaceUri,
                         String sessionId,
                         String jsonContent) {
        addTrace(workspaceUri, sessionId, jsonContent, TraceCollector.MessageType.TRACE);
    }

    public void addTrace(String workspaceUri,
                         String sessionId,
                         String jsonContent,
                         TraceCollector.MessageType messageType) {
        DapTraceMessage message = new DapTraceMessage(
            workspaceUri,
            sessionId,
            Instant.now(),
            jsonContent,
            messageType
        );

        traces.addLast(message);

        // Trim to max size
        while (traces.size() > MAX_TRACE_MESSAGES) {
            traces.pollFirst();
        }

        // Fire CDI event for WebSocket streaming
        traceEvent.fire(message);
    }

    public List<DapTraceMessage> getTracesForSession(String sessionId, int limit) {
        var filtered = traces.stream()
            .filter(t -> t.sessionId().equals(sessionId))
            .toList();
        int skip = Math.max(0, filtered.size() - limit);
        return filtered.stream()
            .skip(skip)
            .toList();
    }

    public void clear() {
        traces.clear();
    }
}
