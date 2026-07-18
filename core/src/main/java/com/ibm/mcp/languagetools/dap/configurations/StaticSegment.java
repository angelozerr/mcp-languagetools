package com.ibm.mcp.languagetools.dap.configurations;

/**
 * A static segment of literal text in the pattern.
 */
public class StaticSegment implements Segment {
    private final String value;

    public StaticSegment(String value) {
        this.value = value;
    }

    @Override
    public String matches(String input) {
        if (input.startsWith(value)) {
            return value;
        }
        return null;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    public String getValue() {
        return value;
    }
}
