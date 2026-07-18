package com.ibm.mcp.languagetools.admin.dto;

import com.ibm.mcp.languagetools.lsp.DocumentSelector;

import java.util.List;
import java.util.Map;

/**
 * LSP server configuration - immutable server descriptor.
 * This represents the LSP server's definition independent of any workspace or runtime state.
 */
public record LspConfigDTO(
    String id,
    String name,
    String description,
    String url,
    List<DocumentSelector> documentSelector,
    String command,
    Map<String, String> env,
    String workingDirectory,
    Map<String, Object> initializationOptions,
    Map<String, Map<String, List<?>>> contributions,
    boolean isExtension
) {
}
