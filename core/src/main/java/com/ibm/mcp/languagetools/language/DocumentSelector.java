package com.ibm.mcp.languagetools.language;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A document selector is a list of {@link DocumentFilter}.
 * Follows the VS Code DocumentSelector API.
 *
 * @see <a href="https://code.visualstudio.com/api/references/document-selector">VS Code DocumentSelector</a>
 */
public class DocumentSelector {

    private final List<DocumentFilter> filters;

    public DocumentSelector(List<DocumentFilter> filters) {
        this.filters = filters != null ? filters : Collections.emptyList();
    }

    /**
     * Check if any filter in this selector matches the given URI and language.
     */
    public boolean matches(URI uri, String language) {
        return matches(uri, language, null);
    }

    /**
     * Check if any filter in this selector matches the given URI and language,
     * scoped to a workspace base path.
     */
    public boolean matches(URI uri, String language, Path basePath) {
        for (DocumentFilter f : filters) {
            if (f.matches(uri, language, basePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the list of language identifiers declared in this selector's filters.
     */
    public List<String> getLanguages() {
        List<String> languages = new ArrayList<>();
        for (DocumentFilter f : filters) {
            if (f.getLanguage() != null && !languages.contains(f.getLanguage())) {
                languages.add(f.getLanguage());
            }
        }
        return languages;
    }

    /**
     * Returns true if this selector has no filters.
     */
    public boolean isEmpty() {
        return filters.isEmpty();
    }

    @Override
    public String toString() {
        return "DocumentSelector{filters=" + filters + "}";
    }
}
