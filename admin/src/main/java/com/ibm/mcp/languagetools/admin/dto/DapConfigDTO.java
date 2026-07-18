package com.ibm.mcp.languagetools.admin.dto;

import com.ibm.mcp.languagetools.lsp.DocumentSelector;

import java.util.List;
import java.util.Map;

/**
 * DAP (Debug Adapter Protocol) configuration DTO.
 * Represents a debug adapter's static configuration.
 */
public record DapConfigDTO(
    String id,
    String name,
    String description,
    String url,
    List<DocumentSelector> documentSelector,
    Map<String, Map<String, List<?>>> contributions
) {
}
