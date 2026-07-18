package com.ibm.mcp.languagetools.dap.trace;

import com.ibm.mcp.languagetools.trace.AbstractTraceCollector;
import com.ibm.mcp.languagetools.trace.TraceKind;

public class DapTraceCollector extends AbstractTraceCollector {

    @Override
    protected TraceKind getTraceKind() {
        return TraceKind.DAP;
    }
}
