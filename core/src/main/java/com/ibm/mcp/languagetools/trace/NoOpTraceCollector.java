package com.ibm.mcp.languagetools.trace;

/**
 * No-op implementation of {@link TraceCollector}.
 * {@link #isEnabled()} returns false so callers skip message creation entirely.
 */
public class NoOpTraceCollector implements TraceCollector {

    public static final NoOpTraceCollector INSTANCE = new NoOpTraceCollector();

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void addTrace(String workspaceUri, String contextId, String content, MessageType type) {
    }
}
