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

import java.util.concurrent.CompletableFuture;

/**
 * A progress monitor that scales progress to a portion of a parent monitor.
 *
 * Example: If start=0.4 and end=0.7, then:
 * - reportProgress(0.0, msg) → parent receives 0.4
 * - reportProgress(0.5, msg) → parent receives 0.55
 * - reportProgress(1.0, msg) → parent receives 0.7
 */
public class SubProgressMonitor implements ProgressMonitor {

    private final ProgressMonitor parent;
    private final String taskId;
    private final double startPercent;
    private final double endPercent;

    public SubProgressMonitor(ProgressMonitor parent, String taskId, double startPercent, double endPercent) {
        this.parent = parent;
        this.taskId = taskId;
        this.startPercent = startPercent;
        this.endPercent = endPercent;
    }

    @Override
    public void reportProgress(double progress, String message) {
        // Scale progress to parent's range
        double scaled = startPercent + (progress * (endPercent - startPercent));
        parent.reportProgress(scaled, message);
    }

    @Override
    public void reportProgress(String message) {
        parent.reportProgress(message);
    }

    @Override
    public void setComplete() {
        // Report 100% within this sub-task's range
        parent.reportProgress(endPercent, "Completed");
    }

    @Override
    public double getTotal() {
        return 1.0; // Sub-monitors always work on 0.0-1.0 scale
    }

    @Override
    public boolean isCancelled() {
        return parent.isCancelled();
    }

    @Override
    public void checkCancelled() {
        parent.checkCancelled();
    }

    @Override
    public <T> CompletableFuture<T> executeWithCancellation(CompletableFuture<T> future) {
        return parent.executeWithCancellation(future);
    }

    @Override
    public boolean isSupported() {
        return parent.isSupported();
    }

    @Override
    public void addStep(String stepId, double weight) {
        // Delegate to parent (but steps are scoped to this sub-monitor)
        // For now, we don't support nested steps - keep it simple
        throw new UnsupportedOperationException("Sub-monitors don't support nested steps yet");
    }

    @Override
    public ProgressMonitor beginStep(String stepId) {
        throw new UnsupportedOperationException("Sub-monitors don't support nested steps yet");
    }

    @Override
    public void completeStep(String stepId) {
        throw new UnsupportedOperationException("Sub-monitors don't support nested steps yet");
    }

    @Override
    public String startTask(String taskId) {
        return parent.startTask(taskId);
    }

    @Override
    public void endTask(String taskId) {
        parent.endTask(taskId);
    }

    @Override
    public void cancel(String taskId) {
        parent.cancel(taskId);
    }

    @Override
    public ProgressMonitor createSubMonitor(double start, double end) {
        // Create nested sub-monitor by scaling ranges
        double nestedStart = startPercent + (start * (endPercent - startPercent));
        double nestedEnd = startPercent + (end * (endPercent - startPercent));
        return new SubProgressMonitor(parent, taskId + "-sub", nestedStart, nestedEnd);
    }
}
