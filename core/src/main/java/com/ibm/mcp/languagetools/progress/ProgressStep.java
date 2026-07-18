package com.ibm.mcp.languagetools.progress;

/**
 * Standard progress steps for LSP server lifecycle operations.
 */
public enum ProgressStep {

    CHECKING("Checking"),
    INSTALLING("Installing"),
    STARTING("Starting"),
    INITIALIZING("Initializing"),
    INDEXING("Indexing"),
    EXECUTING("Executing");

    private final String label;

    ProgressStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
