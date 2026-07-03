package com.redhat.mcp.languagetools.dap.configurations;

/**
 * A dynamic segment like ${address} or ${port} in the pattern.
 */
public abstract class DynamicSegment implements Segment {

    public enum DynamicSegmentType {
        ADDRESS,
        PORT
    }

    private final String value;
    private final DynamicSegmentType type;

    protected DynamicSegment(String value, DynamicSegmentType type) {
        this.value = value;
        this.type = type;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    public String getValue() {
        return value;
    }

    public DynamicSegmentType getType() {
        return type;
    }
}
