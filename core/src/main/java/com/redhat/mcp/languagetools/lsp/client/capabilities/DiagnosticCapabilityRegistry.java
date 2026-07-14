package com.redhat.mcp.languagetools.lsp.client.capabilities;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.lsp.client.LspClientFeatures;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

import java.util.List;
import java.util.function.Predicate;

public class DiagnosticCapabilityRegistry extends TextDocumentServerCapabilityRegistry<DiagnosticRegistrationOptions> {

    private static final Predicate<ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getDiagnosticProvider() != null;

    public DiagnosticCapabilityRegistry(LspClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    static class ExtendedDiagnosticRegistrationOptions extends DiagnosticRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
        private transient ExtendedDocumentSelector documentSelector;

        @Override
        public List<ExtendedDocumentSelector.ExtendedDocumentFilter> getFilters() {
            if (documentSelector == null) {
                documentSelector = new ExtendedDocumentSelector(super.getDocumentSelector());
            }
            return documentSelector.getFilters();
        }
    }

    @Override
    protected DiagnosticRegistrationOptions create(JsonObject registerOptions) {
        return new Gson().fromJson(registerOptions, ExtendedDiagnosticRegistrationOptions.class);
    }

    public boolean isDiagnosticSupported(LanguageDocument document) {
        return super.isSupported(document, SERVER_CAPABILITIES_PREDICATE);
    }
}
