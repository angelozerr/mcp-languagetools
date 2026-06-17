package com.redhat.mcp.languagetools.admin;

import com.redhat.mcp.languagetools.lsp.trace.LspTraceMessage;

import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for the broadcast processor used in SSE trace streaming.
 */
@ApplicationScoped
public class TraceProcessorProducer {

    @Produces
    @ApplicationScoped
    public BroadcastProcessor<LspTraceMessage> createTraceProcessor() {
        return BroadcastProcessor.create();
    }
}
