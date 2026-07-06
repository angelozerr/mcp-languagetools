package com.redhat.mcp.languagetools.lsp.server;

/**
 * Constants for classpath-extensible server contribution fields.
 * Defines the fields that can be contributed to servers supporting classpath extensions.
 */
public final class ClasspathExtensibleContributes {

    /**
     * Classpath field: JAR files to add to the server's classpath.
     * Example: "contributes": { "microprofile": { "classpath": ["lib/*.jar"] } }
     */
    public static final String CLASSPATH = "classpath";

    private ClasspathExtensibleContributes() {
        // Utility class
    }
}
