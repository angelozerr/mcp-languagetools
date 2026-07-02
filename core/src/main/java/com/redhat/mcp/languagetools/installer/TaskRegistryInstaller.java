package com.redhat.mcp.languagetools.installer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.task.InstallerTask;
import com.redhat.mcp.languagetools.installer.task.InstallerTaskRegistry;
import com.redhat.mcp.languagetools.server.ServerConfigBase;
import com.redhat.mcp.languagetools.trace.TraceCollector;
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
    private volatile TraceProgressIndicator progressIndicator;

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

                TraceCollector trace = config.getTraceCollector();
                if (trace != null) {
                    trace.info("Starting installation for: " + config.getName());
                }

                // Check if already installed
                if (installerConfig.has(FIELD_CHECK)) {
                    InstallerTask checkTask = parseTaskNode(installerConfig.get(FIELD_CHECK));
                    if (checkTask != null && checkTask.execute(context)) {
                        // Already installed - extract command
                        String command = extractCommand(context);

                        if (trace != null) {
                            trace.info("Server already installed");
                        }

                        setStatus(context, InstallationStatus.ALREADY_INSTALLED);
                        return new InstallResult(context.getInstallDir(), command, InstallationStatus.ALREADY_INSTALLED);
                    }
                }

                // Not installed - run installation
                setStatus(context, InstallationStatus.INSTALLING);

                if (trace != null) {
                    trace.info("Installing server...");
                }

                if (!installerConfig.has(FIELD_RUN)) {
                    throw new IllegalStateException("No run task defined in installer.json");
                }

                InstallerTask runTask = parseTaskNode(installerConfig.get(FIELD_RUN));
                if (runTask == null) {
                    throw new IllegalStateException("No run task defined in installer.json");
                }

                boolean success = runTask.execute(context);
                if (!success) {
                    setStatus(context, InstallationStatus.FAILED);
                    throw new IllegalStateException("Installation failed");
                }

                // Extract command after installation
                String command = extractCommand(context);

                setStatus(context, InstallationStatus.INSTALLED);

                if (trace != null) {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    trace.info(String.format("Installation completed successfully in %d ms", elapsedMs));
                }

                return new InstallResult(context.getInstallDir(), command, InstallationStatus.INSTALLED);

            } catch (TraceProgressIndicator.CancellationException e) {
                setStatus(context, InstallationStatus.STOPPED);
                TraceCollector trace = config.getTraceCollector();
                if (trace != null) {
                    trace.error("Installation cancelled by user");
                }
                throw new InstallationException("Installation cancelled", e);
            } catch (Exception e) {
                LOG.errorf(e, "Installation failed for %s", config.getServerId());
                setStatus(context, InstallationStatus.FAILED);
                TraceCollector trace = config.getTraceCollector();
                if (trace != null) {
                    trace.error("Installation failed: " + e.getMessage());
                    // Also log the root cause if different
                    Throwable cause = e.getCause();
                    if (cause != null && cause != e) {
                        trace.error("Cause: " + cause.getMessage());
                    }
                }
                throw new InstallationException("Installation failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public InstallationStatus getStatus() {
        return status.get();
    }

    @Override
    public void stop() {
        if (progressIndicator != null) {
            progressIndicator.cancel();
        }
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
}
