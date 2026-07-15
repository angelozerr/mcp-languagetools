package com.redhat.mcp.languagetools.lsp.tools.strategies;

import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.client.LspCapability;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerResolver;
import com.redhat.mcp.languagetools.lsp.tools.params.FileUriRequestParams;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import org.eclipse.lsp4j.Diagnostic;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DiagnosticsStrategy
        extends DidOpenBasedStrategy<FileUriRequestParams, String, List<Diagnostic>> {

    public DiagnosticsStrategy(LanguageRegistry languageRegistry) {
        super(languageRegistry);
    }

    @Override
    public LspCapability getCapability() {
        return LspCapability.DIAGNOSTIC;
    }

    @Override
    public String getTitle() {
        return "Diagnostics";
    }

    @Override
    public CompletableFuture<List<LspServer>> resolveServers(
            LspServerResolver resolver,
            FileUriRequestParams params,
            ProgressMonitor progressMonitor) {
        LanguageDocument document = languageRegistry.createDocument(params.getFileUri());
        return resolver.getLspServersForFile(
                document,
                params.getCwd(),
                LspServer::isEnabled,
                progressMonitor
        );
    }

    @Override
    public String buildLspParams(FileUriRequestParams params) {
        return params.getFileUri();
    }

    @Override
    protected String extractFileUri(String lspParams) {
        return lspParams;
    }

    @Override
    protected CompletableFuture<List<Diagnostic>> executeAfterDiagnostics(LspServer server, String lspParams) {
        List<Diagnostic> cached = server.getDiagnosticsCache().get(lspParams);
        return CompletableFuture.completedFuture(cached != null ? cached : Collections.emptyList());
    }

    @Override
    public List<Diagnostic> getEmptyResult() {
        return Collections.emptyList();
    }

    @Override
    public boolean isValidResult(List<Diagnostic> result) {
        return result != null && !result.isEmpty();
    }

    @Override
    public String formatResults(FileUriRequestParams params, List<List<Diagnostic>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Diagnostics for: ").append(params.getFileUri()).append("\n\n");

        int total = 0;
        for (List<Diagnostic> diagnostics : results) {
            for (Diagnostic diagnostic : diagnostics) {
                sb.append(String.format("  [%s] Line %d: %s\n",
                        diagnostic.getSeverity(),
                        diagnostic.getRange().getStart().getLine() + 1,
                        diagnostic.getMessage()));
                total++;
            }
        }

        if (total == 0) {
            return "No diagnostics found for: " + params.getFileUri();
        }

        sb.insert(sb.indexOf("\n\n") + 2, String.format("Found %d diagnostic(s)\n\n", total));
        return sb.toString();
    }

    @Override
    public String formatNoResultFound(FileUriRequestParams params) {
        return "No diagnostics found for: " + params.getFileUri();
    }
}
