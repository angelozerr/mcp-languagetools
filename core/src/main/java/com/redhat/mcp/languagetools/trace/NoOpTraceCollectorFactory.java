package com.redhat.mcp.languagetools.trace;

import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.redhat.mcp.languagetools.mcp.trace.NoOpMcpTraceCollector;

/**
 * Default factory that creates no-op trace collectors.
 * Used when no admin module is on the classpath.
 */
public class NoOpTraceCollectorFactory implements TraceCollectorFactory {

    @Override
    public TraceCollector createLspTraceCollector() {
        return NoOpTraceCollector.INSTANCE;
    }

    @Override
    public TraceCollector createDapTraceCollector() {
        return NoOpTraceCollector.INSTANCE;
    }

    @Override
    public McpTraceCollector createMcpTraceCollector() {
        return new NoOpMcpTraceCollector();
    }
}
