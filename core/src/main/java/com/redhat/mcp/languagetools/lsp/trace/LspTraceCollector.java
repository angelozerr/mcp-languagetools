package com.redhat.mcp.languagetools.lsp.trace;

import com.redhat.mcp.languagetools.trace.AbstractTraceCollector;
import com.redhat.mcp.languagetools.trace.TraceKind;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterForReflection
public class LspTraceCollector extends AbstractTraceCollector {

    @Override
    protected TraceKind getTraceKind() {
        return TraceKind.LSP;
    }
}
