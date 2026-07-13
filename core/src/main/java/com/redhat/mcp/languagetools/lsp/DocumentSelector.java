package com.redhat.mcp.languagetools.lsp;

import java.util.regex.Pattern;

/**
 * Document selector as defined in LSP specification.
 * Determines which files a language server is interested in.
 */
public class DocumentSelector {

    /**
     * Language identifier (e.g., "java", "typescript")
     */
    private String language;

    /**
     * URI scheme (e.g., "file", "untitled")
     */
    private String scheme;

    /*
     * Glob pattern (e.g., "../*.java")
     */
    private String pattern;

    public DocumentSelector() {
    }

    public DocumentSelector(String language) {
        this.language = language;
    }

    public static DocumentSelector forLanguage(String language) {
        return new DocumentSelector(language);
    }

    public static DocumentSelector forPattern(String pattern) {
        DocumentSelector selector = new DocumentSelector();
        selector.pattern = pattern;
        return selector;
    }

    /**
     * Check if this selector matches the given URI and language.
     */
    public boolean matches(String uri, String language) {
        // Check language
        if (this.language != null && !this.language.equals(language)) {
            return false;
        }

        // Check scheme
        if (this.scheme != null && !uri.startsWith(this.scheme + ":")) {
            return false;
        }

        // Check pattern (simple glob matching)
        if (this.pattern != null) {
            // Build the regex by splitting on glob tokens so that
            // literal characters (including '.') are properly escaped
            // and '**' / '*' are expanded without interfering with each other.
            String[] parts = this.pattern.split("(?<=\\*\\*)|(?=\\*\\*)|(?<=[^*])(?=\\*)|(?<=\\*)(?=[^*])", -1);
            StringBuilder globRegex = new StringBuilder();
            for (String part : parts) {
                if ("**".equals(part)) {
                    globRegex.append(".*");
                } else if ("*".equals(part)) {
                    globRegex.append("[^/]*");
                } else {
                    globRegex.append(Pattern.quote(part));
                }
            }
            return uri.matches(globRegex.toString());
        }

        return true;
    }

    // Getters and setters
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        if (language != null) {
            return "DocumentSelector{language='" + language + "'}";
        }
        if (pattern != null) {
            return "DocumentSelector{pattern='" + pattern + "'}";
        }
        if (scheme != null) {
            return "DocumentSelector{scheme='" + scheme + "'}";
        }
        return "DocumentSelector{}";
    }
}
