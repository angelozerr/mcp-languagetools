package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;
import com.redhat.mcp.languagetools.trace.TraceCollector;

/**
 * Task that configures the server command.
 * This extracts the final command from installer.json.
 */
public class ConfigureServerTask implements InstallerTask {
    private final String name;
    private final String command;

    public ConfigureServerTask(String name, String command) {
        this.name = name;
        this.command = command;
    }

    @Override
    public boolean execute(InstallerContext context) {
        context.checkCanceled();

        String resolvedCommand = context.resolveVariables(command);

        // Store the resolved command in context
        context.setVariable("SERVER_COMMAND", resolvedCommand);

        TraceCollector trace = context.getConfig().getTraceCollector();
        if (trace != null) {
            trace.info("Server command configured: " + resolvedCommand);
        }

        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    /**
     * Factory for ConfigureServerTask.
     */
    public static class Factory implements InstallerTaskFactory {
        @Override
        public String getType() {
            return "configureServer";
        }

        @Override
        public InstallerTask createTask(JsonElement config) {
            JsonObject obj = config.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Configure server";
            String command = obj.get("command").getAsString();
            return new ConfigureServerTask(name, command);
        }
    }
}
