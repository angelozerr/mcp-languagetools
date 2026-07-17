package com.redhat.mcp.languagetools.admin.trace;

import com.redhat.mcp.languagetools.dap.trace.DapTraceCollector;
import com.redhat.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.redhat.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import com.redhat.mcp.languagetools.trace.TraceCollectorFactory;

/**
 * Factory that creates real trace collectors when the admin module is present.
 * Registered via META-INF/services.
 */
public class AdminTraceCollectorFactory implements TraceCollectorFactory {

    @Override
    public TraceCollector createLspTraceCollector() {
        return new LspTraceCollector();
    }

    @Override
    public TraceCollector createDapTraceCollector() {
        return new DapTraceCollector();
    }

    @Override
    public McpTraceCollector createMcpTraceCollector() {
        return new McpTraceCollector();
    }
}
