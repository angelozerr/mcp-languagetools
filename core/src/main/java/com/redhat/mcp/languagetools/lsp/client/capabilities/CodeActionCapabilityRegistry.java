package com.redhat.mcp.languagetools.lsp.client.capabilities;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.lsp.client.LspClientFeatures;
import org.eclipse.lsp4j.CodeActionRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

import java.util.List;
import java.util.function.Predicate;

public class CodeActionCapabilityRegistry extends TextDocumentServerCapabilityRegistry<CodeActionRegistrationOptions> {

    private static final Predicate<ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getCodeActionProvider());

    public CodeActionCapabilityRegistry(LspClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    static class ExtendedCodeActionRegistrationOptions extends CodeActionRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected CodeActionRegistrationOptions create(JsonObject registerOptions) {
        return new Gson().fromJson(registerOptions, ExtendedCodeActionRegistrationOptions.class);
    }

    public boolean isCodeActionSupported(LanguageDocument document) {
        return super.isSupported(document, SERVER_CAPABILITIES_PREDICATE);
    }
}
