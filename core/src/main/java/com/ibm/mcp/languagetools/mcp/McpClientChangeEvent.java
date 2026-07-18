package com.ibm.mcp.languagetools.mcp;

/**
 * CDI event fired when MCP clients connect or disconnect.
 */
public record McpClientChangeEvent() {
    // Simple marker event - we just need to know "something changed"
}
