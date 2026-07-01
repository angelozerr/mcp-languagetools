package com.redhat.mcp.languagetools.dap.session;

import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.dap.trace.DapTraceMessage;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.trace.TraceCollectorBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Wrapper that adapts DapTraceCollector to TraceCollector interface.
 */
class DapTraceCollectorWrapper extends TraceCollectorBase {

    private final DapTraceCollector dapTraceCollector;
    private final String sessionId;
    private final String serverId;

    public DapTraceCollectorWrapper(DapTraceCollector dapTraceCollector,
                                    String sessionId,
                                    String serverId) {
        this.dapTraceCollector = dapTraceCollector;
        this.sessionId = sessionId;
        this.serverId = serverId;
    }

    @Override
    protected void addTrace(String message, MessageType type, String formattedMessage) {
        // Send as SENT message in DAP trace
        // MessageType will be handled by the trace display (UPDATE = replace last line)
        dapTraceCollector.addTrace(
            sessionId,
            serverId,
            DapTraceMessage.MessageDirection.SENT,
            formattedMessage,
            type
        );
    }
}
