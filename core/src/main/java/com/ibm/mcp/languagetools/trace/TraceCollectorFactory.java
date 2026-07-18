package com.ibm.mcp.languagetools.trace;

import com.ibm.mcp.languagetools.mcp.trace.McpTraceCollector;

/**
 * SPI factory for creating trace collectors.
 * <p>
 * When no implementation is found on the classpath (e.g. admin module not present),
 * a {@link NoOpTraceCollector} is used for LSP/DAP and a no-op
 * {@link McpTraceCollector} for MCP — avoiding memory accumulation.
 */
public interface TraceCollectorFactory {

    TraceCollector createLspTraceCollector();

    TraceCollector createDapTraceCollector();

    McpTraceCollector createMcpTraceCollector();
}
