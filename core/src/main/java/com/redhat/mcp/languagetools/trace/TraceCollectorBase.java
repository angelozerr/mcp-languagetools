package com.redhat.mcp.languagetools.trace;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public abstract class TraceCollectorBase implements TraceCollector {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public final void trace(String message, MessageType type) {
        // Format installation messages like trace messages: [Installation - HH:mm:ss] message
        // BUT: UPDATE messages don't get timestamp to avoid flickering when progress updates
        String formattedMessage;
        if (type == MessageType.UPDATE) {
            // No timestamp prefix for UPDATE - just the message (e.g., "Downloading: 3.2 MB / 45 MB (7%)")
            formattedMessage = message;
        } else {
            // Normal messages get timestamp prefix
            formattedMessage = String.format("[Installation - %s] %s",
                    TIME_FORMATTER.format(Instant.now()),
                    message);
        }
        addTrace(message, type, formattedMessage);
    }

    protected abstract void addTrace(String message, MessageType type, String formattedMessage);
}
