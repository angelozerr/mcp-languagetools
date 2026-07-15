package com.redhat.mcp.languagetools.admin.ws;

import com.redhat.mcp.languagetools.admin.dto.WorkspaceDTO;

import java.util.List;

public class WorkspacesUpdateWsMessage extends WsMessage {

    private final List<WorkspaceDTO> workspaces;

    public WorkspacesUpdateWsMessage(List<WorkspaceDTO> workspaces) {
        super(WsMessageType.WORKSPACES_UPDATE);
        this.workspaces = workspaces;
    }

    public List<WorkspaceDTO> getWorkspaces() { return workspaces; }
}
