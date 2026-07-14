package com.redhat.mcp.languagetools.lsp.client.capabilities;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.lsp.client.LspClientFeatures;
import org.eclipse.lsp4j.HoverRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

import java.util.List;
import java.util.function.Predicate;

public class HoverCapabilityRegistry extends TextDocumentServerCapabilityRegistry<HoverRegistrationOptions> {

    private static final Predicate<ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getHoverProvider());

    public HoverCapabilityRegistry(LspClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    static class ExtendedHoverRegistrationOptions extends HoverRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected HoverRegistrationOptions create(JsonObject registerOptions) {
        return new Gson().fromJson(registerOptions, ExtendedHoverRegistrationOptions.class);
    }

    public boolean isHoverSupported(LanguageDocument document) {
        return super.isSupported(document, SERVER_CAPABILITIES_PREDICATE);
    }
}
