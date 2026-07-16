package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;

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
        context.getProgress().beginStep(getName());

        String resolvedCommand = context.resolveVariables(command);
        context.setVariable("SERVER_COMMAND", resolvedCommand);
        context.traceInfo("Server command configured: " + resolvedCommand);

        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

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
