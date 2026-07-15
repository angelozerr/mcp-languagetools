package com.redhat.mcp.languagetools.mcp.trace;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects MCP traffic traces from the Quarkus MCP server traffic logger.
 */
@ApplicationScoped
public class McpTraceCollector {

    public static final com.google.gson.Gson PRETTY_PRINT_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private final List<McpTrace> traces = new CopyOnWriteArrayList<>();
    private static final int MAX_TRACES = 500;

    // Track pending requests: requestId -> PendingRequest
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    @Inject
    Event<McpTrace> traceEvent;

    private record PendingRequest(String method, Instant timestamp, String connectionId) {
    }

    /**
     * Add a new MCP trace with LSP-style formatting.
     */
    public void addTrace(McpTraceDirection direction, RawMessage message, McpConnection connection) {
        Instant now = Instant.now();
        String connectionId = connection.id();
        String formattedMessage = formatMessage(direction, message, connectionId, now);

        McpTrace trace = new McpTrace(
                connectionId,
                formattedMessage,
                now,
                null
        );

        traces.add(trace);

        // Keep only last MAX_TRACES
        if (traces.size() > MAX_TRACES) {
            traces.remove(0);
        }

        // Fire CDI event for SSE broadcasting
        traceEvent.fire(trace);
    }

    /**
     * Format MCP message in LSP-style with method name, ID, and timing.
     */
    private String formatMessage(McpTraceDirection direction, RawMessage message, String connectionId, Instant now) {
        try {
            String method = message.method();
            String id = message.id() != null ? message.id().asString() : null;

            boolean isNotification = id == null && method != null;
            boolean isRequest = id != null && method != null;
            boolean isResponse = id != null && method == null;

            String header;
            String key = connectionId + ":" + (id != null ? id : "");

            if (isRequest) {
                // Sending request: track it
                header = String.format("[Trace - %s] Sending request '%s - (%s)'",
                        formatTime(now), method, id);
                pendingRequests.put(key, new PendingRequest(method, now, connectionId));
            } else if (isResponse) {
                // Receiving response: calculate duration
                PendingRequest pending = pendingRequests.remove(key);
                if (pending != null) {
                    long durationMs = Duration.between(pending.timestamp, now).toMillis();
                    header = String.format("[Trace - %s] Received response '%s - (%s)' in %dms",
                            formatTime(now), pending.method, id, durationMs);
                } else {
                    header = String.format("[Trace - %s] Received response '(%s)'",
                            formatTime(now), id);
                }
            } else if (isNotification && direction == McpTraceDirection.SENT) {
                header = String.format("[Trace - %s] Sending notification '%s'",
                        formatTime(now), method);
            } else if (isNotification && direction == McpTraceDirection.RECEIVED) {
                header = String.format("[Trace - %s] Received notification '%s'",
                        formatTime(now), method);
            } else {
                // Fallback: just show direction
                header = String.format("[Trace - %s] MCP message %s [%s]",
                        formatTime(now), direction, connectionId);
            }

            // Pretty-print JSON body
            String body = getBody(message);
            return header + "\n" + body;

        } catch (Exception e) {
            // If JSON parsing fails, return original messagebody
            String body = message.asString();
            Log.errorf(e, "Failed to parse MCP message for formatting, direction=%s, connectionId=%s, messageStart=%s",
                    direction, connectionId, body.substring(0, Math.min(50, body.length())));
            return String.format("[Trace - %s] MCP %s (PARSE ERROR) [%s]\n%s",
                    formatTime(now), direction, connectionId, body);
        }
    }

    private static String getBody(RawMessage message) {
        try {
            return message.asJsonObject()
                    .encodePrettily();
        } catch (Exception e) {
            String jsonText = message.asString();
            try {
                var json = JsonParser.parseString(jsonText);
                return PRETTY_PRINT_GSON.toJson(json);
            } catch (Exception ex) {
                return jsonText;
            }
        }
    }

    /**
     * Format timestamp as HH:mm:ss.
     */
    private String formatTime(Instant instant) {
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return String.format("%02d:%02d:%02d",
                zdt.getHour(), zdt.getMinute(), zdt.getSecond());
    }

    /**
     * Get traces with limit.
     */
    public List<McpTrace> getTraces(int limit) {
        int size = traces.size();
        int start = Math.max(0, size - limit);
        return List.copyOf(traces.subList(start, size));
    }

    /**
     * Clear all traces.
     */
    public void clearTraces() {
        traces.clear();
        Log.info("MCP traces cleared");
    }
}
