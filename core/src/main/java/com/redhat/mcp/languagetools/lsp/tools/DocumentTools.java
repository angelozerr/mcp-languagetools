package com.redhat.mcp.languagetools.lsp.tools;

import com.redhat.mcp.languagetools.language.LanguageDocument;
import com.redhat.mcp.languagetools.language.LanguageRegistry;
import com.redhat.mcp.languagetools.lsp.server.LspServer;
import com.redhat.mcp.languagetools.lsp.server.LspServerResolver;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
public class DocumentTools {

    @Inject
    LspServerResolver serverResolver;

    @Inject
    LanguageRegistry languageRegistry;

    @Tool(description = "Open a document in language servers to keep it active for multiple LSP operations (references, definition, rename...). "
            + "The file stays open until you call close_document. "
            + "If you don't call open_document, LSP tools will auto-open and auto-close the file for each request. "
            + "Use this when you plan to execute several LSP features on the same file to avoid repeated open/close cycles.")
    public CompletableFuture<String> open_document(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri) {
        LanguageDocument document = languageRegistry.createDocument(fileUri);
        String languageId = languageRegistry.detectLanguage(URI.create(fileUri)).orElse("");
        return serverResolver.getLspServersForFile(
                        document, cwd, LspServer::isEnabled, ProgressMonitor.none())
                .thenCompose(servers -> waitForReadyAndOpen(servers, fileUri, languageId));
    }

    @Tool(description = "Close a document previously opened with open_document. "
            + "Always close documents when you're done with multiple LSP operations on a file.")
    public CompletableFuture<String> close_document(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri) {
        LanguageDocument document = languageRegistry.createDocument(fileUri);
        return serverResolver.getLspServersForFile(
                        document, cwd, LspServer::isEnabled, ProgressMonitor.none())
                .thenApply(servers -> {
                    List<String> closedIn = servers.stream()
                            .filter(server -> server.isExplicitlyOpened(fileUri))
                            .peek(server -> server.closeFileExplicitly(fileUri))
                            .map(server -> server.getConfig().getName())
                            .toList();
                    if (closedIn.isEmpty()) {
                        return "Document was not open: " + fileUri;
                    }
                    return String.format("Closed %s in: %s", fileUri, String.join(", ", closedIn));
                });
    }

    private CompletableFuture<String> waitForReadyAndOpen(List<LspServer> servers, String fileUri, String languageId) {
        if (servers.isEmpty()) {
            return CompletableFuture.completedFuture("No language server found for: " + fileUri);
        }
        List<CompletableFuture<String>> futures = servers.stream()
                .map(server -> server.waitUntilReady()
                        .thenApply(v -> {
                            server.openFileExplicitly(fileUri, languageId);
                            return server.getConfig().getName();
                        }))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    String serverNames = futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.joining(", "));
                    return String.format("Opened %s in: %s", fileUri, serverNames);
                });
    }
}
