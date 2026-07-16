package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ExecTask implements InstallerTask {
    private final String name;
    private final String command;
    private final Integer timeout;

    public ExecTask(String name, String command, Integer timeout) {
        this.name = name;
        this.command = command;
        this.timeout = timeout;
    }

    @Override
    public boolean execute(InstallerContext context) {
        context.checkCanceled();
        context.getProgress().beginStep(getName());

        String resolvedCommand = context.resolveVariables(command);
        context.traceInfo("Executing: " + resolvedCommand);

        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", resolvedCommand);
            } else {
                pb = new ProcessBuilder("sh", "-c", resolvedCommand);
            }
            pb.redirectErrorStream(false);

            Process process = pb.start();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        context.traceInfo(line);
                    }
                } catch (Exception e) {
                    // ignore
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        context.traceError(line);
                    }
                } catch (Exception e) {
                    // ignore
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished;
            if (timeout != null) {
                finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    context.traceInfo("Command timed out after " + timeout + "ms (treated as success)");
                    return true;
                }
            } else {
                process.waitFor();
                finished = true;
            }

            stdoutThread.join(2000);
            stderrThread.join(2000);

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                context.traceInfo("Command completed successfully (exit code 0)");
                return true;
            } else {
                context.traceError("Command failed with exit code " + exitCode);
                return false;
            }
        } catch (Exception e) {
            context.traceError("Failed to execute command: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public static class Factory implements InstallerTaskFactory {
        @Override
        public String getType() {
            return "exec";
        }

        @Override
        public InstallerTask createTask(JsonElement config) {
            JsonObject obj = config.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Execute command";
            String command = obj.get("command").getAsString();
            Integer timeout = obj.has("timeout") ? obj.get("timeout").getAsInt() : null;
            return new ExecTask(name, command, timeout);
        }
    }
}
