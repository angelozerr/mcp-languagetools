package com.redhat.mcp.languagetools.lsp.client.capabilities;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.lsp.client.LspClientFeatures;
import org.eclipse.lsp4j.DocumentSymbolRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

import java.util.List;
import java.util.function.Predicate;

public class DocumentSymbolCapabilityRegistry extends TextDocumentServerCapabilityRegistry<DocumentSymbolRegistrationOptions> {

    private static final Predicate<ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getDocumentSymbolProvider());

    public DocumentSymbolCapabilityRegistry(LspClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    static class ExtendedDocumentSymbolRegistrationOptions extends DocumentSymbolRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected DocumentSymbolRegistrationOptions create(JsonObject registerOptions) {
        return new Gson().fromJson(registerOptions, ExtendedDocumentSymbolRegistrationOptions.class);
    }

    public boolean isDocumentSymbolSupported(LanguageDocument document) {
        return super.isSupported(document, SERVER_CAPABILITIES_PREDICATE);
    }
}
