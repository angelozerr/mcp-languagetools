package com.redhat.mcp.languagetools.admin.dto;

import com.redhat.mcp.languagetools.lsp.DocumentSelector;

import java.util.List;
import java.util.Map;

/**
 * DAP (Debug Adapter Protocol) server configuration DTO.
 * Represents a debug adapter's static configuration.
 */
public record DapServerDTO(
    String id,
    String name,
    String description,
    List<DocumentSelector> documentSelector,
    Map<String, Map<String, List<?>>> contributions
) {
}
