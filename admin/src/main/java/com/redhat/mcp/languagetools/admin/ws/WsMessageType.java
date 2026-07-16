package com.redhat.mcp.languagetools.admin.ws;

import com.fasterxml.jackson.annotation.JsonValue;

public enum WsMessageType {
    LSP_TRACE("lsp-trace"),
    DAP_TRACE("dap-trace"),
    MCP_TRACE("mcp-trace"),
    PROGRESS_INIT("progress-init"),
    PROGRESS_UPDATE("progress-update"),
    SERVER_STATUS_CHANGED("server-status-changed"),
    WORKSPACES_UPDATE("workspaces-update"),
    MCP_CLIENTS_UPDATE("mcp-clients-update"),
    DAP_SESSION_UPDATE("dap-session-update"),
    TRACE_LEVEL_UPDATE("trace-level-update");

    private final String label;

    WsMessageType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
