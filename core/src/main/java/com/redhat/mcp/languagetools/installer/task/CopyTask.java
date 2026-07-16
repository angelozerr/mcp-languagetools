package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CopyTask implements InstallerTask {
    private static final Logger LOG = Logger.getLogger(CopyTask.class);

    private final String name;
    private final String source;
    private final String destination;
    private final InstallerTask onSuccessTask;

    public CopyTask(String name, String source, String destination, InstallerTask onSuccessTask) {
        this.name = name;
        this.source = source;
        this.destination = destination;
        this.onSuccessTask = onSuccessTask;
    }

    @Override
    public boolean execute(InstallerContext context) {
        context.checkCanceled();
        context.getProgress().beginStep(getName());

        String resolvedSource = context.resolveVariables(source);
        String resolvedDestination = context.resolveVariables(destination);

        context.traceInfo("Copying from: " + resolvedSource + " to: " + resolvedDestination);
        context.getProgress().reportProgress("Copying " + name);

        try {
            Path destPath = Paths.get(resolvedDestination);
            Files.createDirectories(destPath.getParent());

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream resourceStream = classLoader.getResourceAsStream(resolvedSource.startsWith("/") ? resolvedSource.substring(1) : resolvedSource);
            if (resourceStream == null) {
                throw new IOException("Resource not found: " + resolvedSource);
            }

            try (InputStream is = resourceStream) {
                Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING);
            }

            context.getProgress().reportProgress(100, "Copy complete");
            context.traceInfo("Copied to: " + resolvedDestination);

            if (onSuccessTask != null) {
                return onSuccessTask.execute(context);
            }

            return true;

        } catch (IOException e) {
            LOG.errorf(e, "Copy failed: %s -> %s", resolvedSource, resolvedDestination);
            context.traceError("Copy failed: " + e.getMessage());
            throw new IllegalStateException("Copy '" + name + "' failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public static class Factory implements InstallerTaskFactory {
        private static volatile InstallerTaskRegistry registry;

        public Factory() {
        }

        private static InstallerTaskRegistry getRegistry() {
            if (registry == null) {
                synchronized (Factory.class) {
                    if (registry == null) {
                        registry = new InstallerTaskRegistry();
                    }
                }
            }
            return registry;
        }

        @Override
        public String getType() {
            return "copy";
        }

        @Override
        public InstallerTask createTask(JsonElement config) {
            JsonObject obj = config.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Copy";
            String source = obj.get("source").getAsString();
            String destination = obj.get("destination").getAsString();

            InstallerTask onSuccessTask = null;
            if (obj.has("onSuccess")) {
                JsonElement onSuccess = obj.get("onSuccess");
                onSuccessTask = parseTaskNode(onSuccess);
            }

            return new CopyTask(name, source, destination, onSuccessTask);
        }

        private InstallerTask parseTaskNode(JsonElement taskNode) {
            if (taskNode == null || !taskNode.isJsonObject()) {
                return null;
            }

            JsonObject taskObj = taskNode.getAsJsonObject();
            if (taskObj.size() == 0) {
                return null;
            }

            String taskType = taskObj.keySet().iterator().next();
            JsonElement taskConfig = taskObj.get(taskType);

            return getRegistry().createTask(taskType, taskConfig);
        }
    }
}
