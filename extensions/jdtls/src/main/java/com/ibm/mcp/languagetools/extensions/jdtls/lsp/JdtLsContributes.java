package com.ibm.mcp.languagetools.extensions.jdtls.lsp;

/**
 * Constants for JDT.LS contribution fields.
 * Defines the fields that can be contributed to JDT.LS via contributes.jdtls.
 */
public final class JdtLsContributes {

    /**
     * Bundles field: JAR files to load as OSGi bundles in JDT.LS.
     * Example: "contributes": { "jdtls": { "bundles": ["plugins/*.jar"] } }
     */
    public static final String BUNDLES = "bundles";

    private JdtLsContributes() {
        // Utility class
    }
}
