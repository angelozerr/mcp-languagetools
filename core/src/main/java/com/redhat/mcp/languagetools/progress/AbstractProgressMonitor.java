/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.mcp.languagetools.progress;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for ProgressMonitor implementations.
 * Provides common functionality for tracking total and current progress,
 * managing steps, and tracking tasks.
 */
public abstract class AbstractProgressMonitor implements ProgressMonitor {

    private static final AtomicLong TASK_COUNTER = new AtomicLong(0);

    protected final double total;
    protected volatile double current = 0;

    // Step management
    private final Map<String, StepInfo> steps = new LinkedHashMap<>();
    private double totalWeight = 0.0;
    private double completedWeight = 0.0;
    private String currentStepId;

    // Task tracking
    private final Set<String> cancelledTasks = ConcurrentHashMap.newKeySet();
    private String currentTaskId;

    /**
     * Create a progress monitor with default total of 100.
     */
    protected AbstractProgressMonitor() {
        this(100.0);
    }

    /**
     * Create a progress monitor with a specific total.
     *
     * @param total The total expected progress value
     */
    protected AbstractProgressMonitor(double total) {
        this.total = total;
    }

    /**
     * Get the total expected progress value.
     *
     * @return The total progress value
     */
    public double getTotal() {
        return total;
    }

    /**
     * Get the current progress value.
     *
     * @return The current progress value
     */
    public double getCurrent() {
        return current;
    }

    /**
     * Update the current progress value.
     *
     * @param current The new current progress value
     */
    protected void setCurrent(double current) {
        this.current = current;
    }

    @Override
    public void addStep(String stepId, double weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Step weight must be positive: " + weight);
        }

        // Store weight only - calculate ranges dynamically in beginStep()
        // This makes it safer: ranges are always recalculated based on current state
        steps.put(stepId, new StepInfo(stepId, weight, 0.0, 0.0));
        totalWeight += weight;
    }

    @Override
    public ProgressMonitor beginStep(String stepId) {
        StepInfo step = steps.get(stepId);
        if (step == null) {
            throw new IllegalArgumentException("Step not declared: " + stepId + ". Call addStep() first.");
        }

        // Auto-complete previous step
        if (currentStepId != null && !currentStepId.equals(stepId)) {
            completeStep(currentStepId);
        }

        currentStepId = stepId;
        step.started = true;

        // Calculate ranges dynamically based on current completed weight
        double startPercent = totalWeight > 0 ? completedWeight / totalWeight : 0.0;
        double endPercent = totalWeight > 0 ? (completedWeight + step.weight) / totalWeight : 1.0;

        // Update step info with calculated ranges
        step.startPercent = startPercent;
        step.endPercent = endPercent;

        // Return sub-monitor scaled to this step's range
        return new SubProgressMonitor(this, stepId, startPercent, endPercent);
    }

    @Override
    public void completeStep(String stepId) {
        StepInfo step = steps.get(stepId);
        if (step != null && !step.completed) {
            step.completed = true;
            completedWeight += step.weight;
            double progressPercent = totalWeight > 0 ? completedWeight / totalWeight : 1.0;
            reportProgress(progressPercent, "Completed " + stepId);
        }
    }

    @Override
    public String startTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            taskId = "task-" + TASK_COUNTER.incrementAndGet();
        }
        currentTaskId = taskId;
        return taskId;
    }

    @Override
    public void endTask(String taskId) {
        if (taskId != null && taskId.equals(currentTaskId)) {
            currentTaskId = null;
        }
        // Remove from cancelled set to avoid memory leak
        cancelledTasks.remove(taskId);
    }

    @Override
    public void cancel(String taskId) {
        if (taskId != null) {
            cancelledTasks.add(taskId);
        }
    }

    @Override
    public boolean isCancelled() {
        return currentTaskId != null && cancelledTasks.contains(currentTaskId);
    }

    @Override
    public ProgressMonitor createSubMonitor(double start, double end) {
        if (start < 0 || start > 1 || end < 0 || end > 1 || start > end) {
            throw new IllegalArgumentException("Invalid range: start=" + start + ", end=" + end);
        }
        return new SubProgressMonitor(this, currentTaskId + "-sub", start, end);
    }

    /**
     * Internal class to track step information.
     */
    private static class StepInfo {
        final String id;
        final double weight;
        double startPercent;  // Calculated dynamically in beginStep()
        double endPercent;    // Calculated dynamically in beginStep()
        boolean started;
        boolean completed;

        StepInfo(String id, double weight, double startPercent, double endPercent) {
            this.id = id;
            this.weight = weight;
            this.startPercent = startPercent;
            this.endPercent = endPercent;
        }
    }
}
