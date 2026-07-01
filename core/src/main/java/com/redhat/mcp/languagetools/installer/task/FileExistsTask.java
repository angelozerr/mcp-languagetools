package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;
import com.redhat.mcp.languagetools.trace.TraceCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Task that checks if a file exists.
 */
public class FileExistsTask implements InstallerTask {
    private final String name;
    private final String file;

    public FileExistsTask(String name, String file) {
        this.name = name;
        this.file = file;
    }

    @Override
    public boolean execute(InstallerContext context) {
        context.checkCanceled();

        String resolvedPath = context.resolveVariables(file);
        Path path = Paths.get(resolvedPath);

        boolean exists = Files.exists(path);

        TraceCollector trace = context.getConfig().getTraceCollector();
        if (trace != null) {
            if (exists) {
                trace.info("File exists: " + resolvedPath);
            } else {
                trace.info("File not found: " + resolvedPath);
            }
        }

        return exists;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Factory for FileExistsTask.
     */
    public static class Factory implements InstallerTaskFactory {
        @Override
        public String getType() {
            return "fileExists";
        }

        @Override
        public InstallerTask createTask(JsonElement config) {
            JsonObject obj = config.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Check file exists";
            String file = obj.get("file").getAsString();
            return new FileExistsTask(name, file);
        }
    }
}
