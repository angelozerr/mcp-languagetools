package com.redhat.mcp.languagetools.lsp.tools.params;

public class RenameRequestParams extends FilePositionRequestParams {

    private final String newName;

    public RenameRequestParams(String cwd, String fileUri, int line, int character, String newName) {
        super(cwd, fileUri, line, character);
        this.newName = newName;
    }

    public String getNewName() {
        return newName;
    }
}
