package com.ibm.mcp.languagetools.admin.ws;

import com.ibm.mcp.languagetools.admin.dto.McpClientDTO;

import java.util.List;

public class McpClientsUpdateWsMessage extends WsMessage {

    private final List<McpClientDTO> clients;

    public McpClientsUpdateWsMessage(List<McpClientDTO> clients) {
        super(WsMessageType.MCP_CLIENTS_UPDATE);
        this.clients = clients;
    }

    public List<McpClientDTO> getClients() { return clients; }
}
