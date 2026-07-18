package com.ibm.mcp.languagetools.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkspaceConfiguration} with SPI providers and strategies.
 */
class WorkspaceConfigurationTest {

    private static final WorkspaceConfigurationProvider VSCODE = new VsCodeConfigurationProvider();
    private static final WorkspaceConfigurationProvider BOB = new BobConfigurationProvider();

    private void writeSettingsFile(Path dir, String json) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"), json);
    }

    // --- FIRST_FOUND ---

    @Test
    void firstFoundVsCode(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".vscode"), """
                {"editor.fontSize": 14}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE, BOB),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertEquals(14.0, config.get("editor.fontSize"));
    }

    @Test
    void firstFoundBob(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".bob"), """
                {"bob.theme": "dark"}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE, BOB),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertEquals("dark", config.get("bob.theme"));
    }

    @Test
    void firstFoundPriorityVsCodeFirst(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".vscode"), """
                {"source": "vscode"}
                """);
        writeSettingsFile(root.resolve(".bob"), """
                {"source": "bob"}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE, BOB),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertEquals("vscode", config.get("source"));
    }

    @Test
    void firstFoundPriorityBobFirst(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".vscode"), """
                {"source": "vscode"}
                """);
        writeSettingsFile(root.resolve(".bob"), """
                {"source": "bob"}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(BOB, VSCODE),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertEquals("bob", config.get("source"));
    }

    // --- MERGE ---

    @Test
    void mergeStrategy(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".vscode"), """
                {"shared": "from-vscode", "vscode.only": true}
                """);
        writeSettingsFile(root.resolve(".bob"), """
                {"shared": "from-bob", "bob.only": true}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE, BOB),
                WorkspaceConfigurationStrategy.MERGE);

        // First provider wins on conflicts
        assertEquals("from-vscode", config.get("shared"));
        // Unique keys from both are present
        assertEquals(true, config.get("vscode.only"));
        assertEquals(true, config.get("bob.only"));
    }

    // --- No file ---

    @Test
    void noFileExists(@TempDir Path root) {
        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE, BOB),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertTrue(config.getAll().isEmpty());
    }

    // --- Reload ---

    @Test
    void reload(@TempDir Path root) throws IOException {
        writeSettingsFile(root.resolve(".vscode"), """
                {"version": 1}
                """);

        var config = new WorkspaceConfiguration(root,
                List.of(VSCODE),
                WorkspaceConfigurationStrategy.FIRST_FOUND);

        assertEquals(1.0, config.get("version"));

        // Overwrite and reload
        Files.writeString(root.resolve(".vscode").resolve("settings.json"), """
                {"version": 2}
                """);
        config.reload();

        assertEquals(2.0, config.get("version"));
    }
}
