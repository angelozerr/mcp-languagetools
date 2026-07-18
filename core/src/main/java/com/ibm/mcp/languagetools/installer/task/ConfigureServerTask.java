package com.ibm.mcp.languagetools.installer.task;

import com.google.gson.JsonObject;
import com.ibm.mcp.languagetools.installer.InstallerContext;
import com.ibm.mcp.languagetools.utils.OSUtils;

public class ConfigureServerTask extends InstallerTask {
    private final String command;

    public ConfigureServerTask(String name, InstallerTask onSuccess, String command) {
        super(name, onSuccess);
        this.command = command;
    }

    @Override
    protected boolean run(InstallerContext context) {
        String resolvedCommand = context.resolveVariables(command);
        context.setVariable("SERVER_COMMAND", resolvedCommand);
        context.traceInfo("Server command configured: " + resolvedCommand);
        return true;
    }

    public String getCommand() {
        return command;
    }

    public static class Factory extends InstallerTaskFactoryBase {
        @Override
        public String getType() {
            return "configureServer";
        }

        @Override
        protected String getDefaultName() {
            return "Configure server";
        }

        @Override
        protected InstallerTask create(String name, InstallerTask onSuccess, JsonObject json) {
            String command = OSUtils.getStringFromOs(json, "command");
            return new ConfigureServerTask(name, onSuccess, command);
        }
    }
}
