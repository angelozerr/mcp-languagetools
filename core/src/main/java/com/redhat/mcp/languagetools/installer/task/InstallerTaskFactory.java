package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;

/**
 * Factory for creating installer tasks from JSON.
 * Uses Gson for JSON parsing.
 */
public interface InstallerTaskFactory {
    /**
     * Gets the task type this factory handles (e.g., "download", "fileExists").
     */
    String getType();

    /**
     * Creates a task from JSON configuration.
     *
     * @param config JSON configuration for the task (Gson JsonElement)
     * @return The created task
     */
    InstallerTask createTask(JsonElement config);
}
