package com.ibm.mcp.languagetools.admin.dto;

import java.util.List;

public record WorkspaceDTO(
    String rootUri,
    boolean initialized,
    List<McpClientInfo> mcpClients
) {
    public record McpClientInfo(
        String name,
        String connectedAt
    ) {}
}
