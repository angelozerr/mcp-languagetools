package com.ibm.mcp.languagetools.admin.trace;

import com.ibm.mcp.languagetools.dap.trace.DapTraceCollector;
import com.ibm.mcp.languagetools.lsp.trace.LspTraceCollector;
import com.ibm.mcp.languagetools.mcp.trace.McpTraceCollector;
import com.ibm.mcp.languagetools.trace.TraceCollector;
import com.ibm.mcp.languagetools.trace.TraceCollectorFactory;

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
