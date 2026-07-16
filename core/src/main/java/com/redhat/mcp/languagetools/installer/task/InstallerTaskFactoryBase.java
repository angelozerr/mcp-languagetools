package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Base class for installer task factories.
 * Handles generic parsing of {@code name} and {@code onSuccess} fields,
 * so subclasses only need to parse their own task-specific configuration.
 */
public abstract class InstallerTaskFactoryBase implements InstallerTaskFactory {

    private static volatile InstallerTaskRegistry registry;

    private static InstallerTaskRegistry getRegistry() {
        if (registry == null) {
            synchronized (InstallerTaskFactoryBase.class) {
                if (registry == null) {
                    registry = new InstallerTaskRegistry();
                }
            }
        }
        return registry;
    }

    @Override
    public final InstallerTask createTask(JsonElement config) {
        JsonObject obj = config.getAsJsonObject();
        String name = obj.has("name") ? obj.get("name").getAsString() : getDefaultName();
        InstallerTask onSuccess = loadOnSuccess(obj);
        return create(name, onSuccess, obj);
    }

    /**
     * Create the task with already-parsed common fields.
     *
     * @param name      the task name
     * @param onSuccess the onSuccess task (may be null)
     * @param json      the full JSON config for task-specific fields
     * @return the created task
     */
    protected abstract InstallerTask create(String name, InstallerTask onSuccess, JsonObject json);

    /**
     * Default name when "name" is not specified in JSON.
     */
    protected abstract String getDefaultName();

    private InstallerTask loadOnSuccess(JsonObject json) {
        if (!json.has("onSuccess")) {
            return null;
        }
        return parseTaskNode(json.get("onSuccess"));
    }

    /**
     * Parse a task node (first key = task type, value = task config).
     * Shared across all factories — no more duplication.
     */
    protected static InstallerTask parseTaskNode(JsonElement taskNode) {
        if (taskNode == null || !taskNode.isJsonObject()) {
            return null;
        }
        JsonObject taskObj = taskNode.getAsJsonObject();
        if (taskObj.isEmpty()) {
            return null;
        }
        String taskType = taskObj.keySet().iterator().next();
        JsonElement taskConfig = taskObj.get(taskType);
        return getRegistry().createTask(taskType, taskConfig);
    }
}
