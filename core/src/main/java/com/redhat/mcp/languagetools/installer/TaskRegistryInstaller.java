package com.redhat.mcp.languagetools.installer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.task.DownloadTask;
import com.redhat.mcp.languagetools.installer.task.InstallerTask;
import com.redhat.mcp.languagetools.installer.task.InstallerTaskRegistry;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.server.ServerConfigBase;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server installer that uses task registry to execute installer.json.
 * Uses Gson exclusively for JSON parsing.
 */
public class TaskRegistryInstaller implements ServerInstaller {
    private static final Logger LOG = Logger.getLogger(TaskRegistryInstaller.class);

    // JSON field names
    private static final String FIELD_CHECK = "check";
    private static final String FIELD_RUN = "run";

    private final ServerConfigBase config;
    private final InstallerTaskRegistry registry;
    private final Gson gson;
    private final AtomicReference<InstallationStatus> status = new AtomicReference<>(InstallationStatus.NOT_INSTALLED);

    public TaskRegistryInstaller(ServerConfigBase config) {
        this.config = config;
        this.registry = new InstallerTaskRegistry();
        this.gson = new Gson();
    }

    /**
     * Set installation status and notify context.
     */
    private void setStatus(InstallerContext context, InstallationStatus installStatus) {
        status.set(installStatus);
        context.notifyInstallationStatusChange(installStatus);
    }

    @Override
    public CompletableFuture<InstallResult> ensureInstalled(InstallerContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Parse installer.json
                JsonElement installerConfigJson = config.getInstallerConfig();
                if (installerConfigJson == null || !installerConfigJson.isJsonObject()) {
                    throw new IllegalStateException("No installer configuration found for " + config.getServerId());
                }

                JsonObject installerConfig = installerConfigJson.getAsJsonObject();

                context.traceInfo("Starting installation for: " + config.getName());

                // Check if already installed (skip when force install)
                if (!context.isForceInstall() && installerConfig.has(FIELD_CHECK)) {
                    context.getProgress().beginStep(com.redhat.mcp.languagetools.progress.ProgressStep.CHECKING);
                    InstallerTask checkTask = parseTaskNode(installerConfig.get(FIELD_CHECK));
                    if (checkTask != null && checkTask.execute(context)) {
                        String command = extractCommand(context);
                        context.traceInfo("Server already installed");
                        setStatus(context, InstallationStatus.ALREADY_INSTALLED);
                        return new InstallResult(context.getInstallDir(), command, InstallationStatus.ALREADY_INSTALLED);
                    }
                }

                // Not installed - run installation
                context.getProgress().beginStep(com.redhat.mcp.languagetools.progress.ProgressStep.INSTALLING);
                setStatus(context, InstallationStatus.INSTALLING);
                context.traceInfo("Installing server...");

                if (!installerConfig.has(FIELD_RUN)) {
                    throw new IllegalStateException("No run task defined in installer.json");
                }

                InstallerTask runTask = parseTaskNode(installerConfig.get(FIELD_RUN));
                if (runTask == null) {
                    JsonObject runObj = installerConfig.getAsJsonObject(FIELD_RUN);
                    String taskType = runObj != null && runObj.size() > 0 ? runObj.keySet().iterator().next() : "unknown";
                    throw new IllegalStateException("Failed to create run task of type '" + taskType + "' from installer.json (registered factories: " + registry.getRegisteredTypes() + ")");
                }

                boolean success = runTask.execute(context);
                if (!success) {
                    setStatus(context, InstallationStatus.FAILED);
                    throw new IllegalStateException("Task '" + runTask.getName() + "' failed");
                }

                // Extract command after installation
                String command = extractCommand(context);

                setStatus(context, InstallationStatus.INSTALLED);
                long elapsedMs = System.currentTimeMillis() - startTime;
                context.traceInfo(String.format("Installation completed successfully in %d ms", elapsedMs));

                return new InstallResult(context.getInstallDir(), command, InstallationStatus.INSTALLED);

            } catch (java.util.concurrent.CancellationException e) {
                setStatus(context, InstallationStatus.STOPPED);
                context.traceError("Installation cancelled by user");
                throw new InstallationException("Installation cancelled", e);
            } catch (Exception e) {
                LOG.errorf(e, "Installation failed for %s", config.getServerId());
                setStatus(context, InstallationStatus.FAILED);
                context.traceError("Installation failed: " + e.getMessage());
                Throwable cause = e.getCause();
                if (cause != null && cause != e) {
                    context.traceError("Cause: " + cause.getMessage());
                }
                throw new InstallationException(e.getMessage(), e);
            }
        });
    }

    @Override
    public InstallationStatus getStatus() {
        return status.get();
    }

    @Override
    public void stop() {
        // Cancellation is handled via InstallerContext.checkCanceled()
        status.set(InstallationStatus.STOPPED);
    }

    private InstallerTask parseTaskNode(JsonElement taskNode) {
        if (taskNode == null || !taskNode.isJsonObject()) {
            return null;
        }

        JsonObject taskObj = taskNode.getAsJsonObject();

        // Find the task type (first key in the object)
        if (taskObj.size() == 0) {
            return null;
        }

        String taskType = taskObj.keySet().iterator().next();
        JsonElement taskConfig = taskObj.get(taskType);

        return registry.createTask(taskType, taskConfig);
    }

    /**
     * Extracts the server command from installer.json.
     * The command comes from the configureServer task.
     */
    private String extractCommand(InstallerContext context) {
        // The command was stored in context by ConfigureServerTask
        String command = context.getVariable("SERVER_COMMAND");

        if (command == null) {
            LOG.warnf("No command configured for %s", config.getServerId());
        }

        return command;
    }

    /**
     * Configure progress steps from installer.json task tree.
     * Walks the check and run task nodes (including onSuccess chains)
     * and adds a step for each declared task.
     *
     * @param progress the progress monitor to configure
     * @param installerConfig the installer.json content
     * @param force true to skip the check task
     */
    public static void configureInstallerSteps(ProgressMonitor progress, JsonElement installerConfig, boolean force) {
        if (installerConfig == null || !installerConfig.isJsonObject()) {
            return;
        }
        JsonObject config = installerConfig.getAsJsonObject();

        if (!force && config.has(FIELD_CHECK)) {
            collectTaskSteps(config.get(FIELD_CHECK), progress);
        }

        if (config.has(FIELD_RUN)) {
            collectTaskSteps(config.get(FIELD_RUN), progress);
        }
    }

    private static void collectTaskSteps(JsonElement taskNode, ProgressMonitor progress) {
        if (taskNode == null || !taskNode.isJsonObject()) {
            return;
        }
        JsonObject taskObj = taskNode.getAsJsonObject();
        if (taskObj.isEmpty()) {
            return;
        }

        String taskType = taskObj.keySet().iterator().next();
        JsonElement taskConfigElement = taskObj.get(taskType);
        if (taskConfigElement == null || !taskConfigElement.isJsonObject()) {
            return;
        }
        JsonObject taskConfig = taskConfigElement.getAsJsonObject();

        String name = taskConfig.has("name") ? taskConfig.get("name").getAsString() : taskType;
        double weight = getTaskWeight(taskType);
        progress.addStep(name, weight);

        if ("download".equals(taskType)) {
            progress.addStep(DownloadTask.getExtractStepName(name), 5.0);
        }

        if (taskConfig.has("onSuccess")) {
            collectTaskSteps(taskConfig.get("onSuccess"), progress);
        }
    }

    private static double getTaskWeight(String taskType) {
        return switch (taskType) {
            case "download" -> 20.0;
            case "copy" -> 10.0;
            case "fileExists" -> 1.0;
            case "configureServer" -> 1.0;
            default -> 5.0;
        };
    }
}
