package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileExistsTask extends InstallerTask {
    private final String file;

    public FileExistsTask(String name, InstallerTask onSuccess, String file) {
        super(name, onSuccess);
        this.file = file;
    }

    @Override
    protected boolean run(InstallerContext context) {
        String resolvedPath = context.resolveVariables(file);
        boolean exists;

        if (resolvedPath.contains("*") || resolvedPath.contains("?")) {
            exists = matchesGlob(resolvedPath);
        } else {
            exists = Files.exists(Paths.get(resolvedPath));
        }

        if (exists) {
            context.traceInfo("File exists: " + resolvedPath);
        } else {
            context.traceInfo("File not found: " + resolvedPath);
        }

        return exists;
    }

    private boolean matchesGlob(String globPath) {
        String normalized = globPath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash < 0) {
            return false;
        }
        Path dir = Paths.get(normalized.substring(0, lastSlash));
        String pattern = normalized.substring(lastSlash + 1);

        if (!Files.isDirectory(dir)) {
            return false;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> matcher.matches(p.getFileName()));
        } catch (IOException e) {
            return false;
        }
    }

    public static class Factory extends InstallerTaskFactoryBase {
        @Override
        public String getType() {
            return "fileExists";
        }

        @Override
        protected String getDefaultName() {
            return "Check file exists";
        }

        @Override
        protected InstallerTask create(String name, InstallerTask onSuccess, JsonObject json) {
            String file = json.get("file").getAsString();
            return new FileExistsTask(name, onSuccess, file);
        }
    }
}
