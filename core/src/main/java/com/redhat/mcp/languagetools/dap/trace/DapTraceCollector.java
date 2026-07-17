package com.redhat.mcp.languagetools.dap.trace;

import com.redhat.mcp.languagetools.trace.AbstractTraceCollector;
import com.redhat.mcp.languagetools.trace.TraceKind;

public class DapTraceCollector extends AbstractTraceCollector {

    @Override
    protected TraceKind getTraceKind() {
        return TraceKind.DAP;
    }
}
