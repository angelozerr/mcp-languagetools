package com.redhat.mcp.languagetools.extensions.microprofile.lsp;

import com.redhat.mcp.languagetools.lsp.server.ClasspathExtensibleLspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerConfig;
import com.redhat.mcp.languagetools.workspace.Workspace;
import org.eclipse.lsp4j.CodeActionParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom LSP server for MicroProfile LS.
 * Builds parameters matching the delegate command handlers registered in JDT.LS
 * by the lsp4mp JDT extension (MicroProfileDelegateCommandHandlerForJava).
 */
public class MicroProfileLspServer extends ClasspathExtensibleLspServer {

    public MicroProfileLspServer(LspServerConfig config, Workspace workspace) {
        super(config, workspace);
    }

    /**
     * Builds params for the "microprofile/java/diagnostics" delegate command handler.
     * Expected by MicroProfileDelegateCommandHandlerForJava#createMicroProfileJavaDiagnosticsParams:
     * {@code {"uris": ["file:///path/to/File.java"]}}
     */
    @Override
    public Object buildDiagnosticsRequestParams(String fileUri) {
        return Map.of("uris", List.of(fileUri));
    }

    /**
     * Builds params for the "microprofile/java/codeAction" delegate command handler.
     * Expected by MicroProfileDelegateCommandHandlerForJava#createMicroProfileJavaCodeActionParams:
     * {@code {"textDocument": {"uri": "..."}, "range": {...}, "context": {...},
     *         "resourceOperationSupported": true, "commandConfigurationUpdateSupported": true,
     *         "resolveSupported": true}}
     */
    @Override
    public Object buildCodeActionRequestParams(String fileUri, CodeActionParams codeActionParams) {
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", codeActionParams.getTextDocument());
        params.put("range", codeActionParams.getRange());
        params.put("context", codeActionParams.getContext());
        params.put("resourceOperationSupported", true);
        params.put("commandConfigurationUpdateSupported", true);
        params.put("resolveSupported", true);
        return params;
    }
}
