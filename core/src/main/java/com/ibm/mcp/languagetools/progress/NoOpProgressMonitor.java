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
package com.ibm.mcp.languagetools.progress;

import java.util.concurrent.CompletableFuture;

/**
 * No-op implementation of ProgressMonitor that does nothing.
 * Used when progress tracking is not needed or not available.
 */
public class NoOpProgressMonitor implements ProgressMonitor {

    public static final ProgressMonitor INSTANCE = new NoOpProgressMonitor();

    private NoOpProgressMonitor() {
    }

    @Override
    public void reportProgress(double progress, String message) {
        // No-op
    }

    @Override
    public void reportProgress(String message) {
        // No-op
    }

    @Override
    public void setComplete() {
        // No-op
    }

    @Override
    public double getTotal() {
        return 100.0;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void checkCancelled() {
        // No-op - never cancelled
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public <T> CompletableFuture<T> executeWithCancellation(CompletableFuture<T> future) {
        // No cancellation support, return future as-is
        return future;
    }

    @Override
    public ProgressMonitor addStep(String stepId, double weight) {
        // No-op
        return this;
    }

    @Override
    public ProgressMonitor beginStep(String stepId) {
        return this; // Return self
    }

    @Override
    public void completeStep(String stepId) {
        // No-op
    }

    @Override
    public String startTask(String taskId) {
        return taskId;
    }

    @Override
    public void endTask(String taskId) {
        // No-op
    }

    @Override
    public void cancel(String taskId) {
        // No-op - cannot be cancelled
    }

    @Override
    public ProgressMonitor createSubMonitor(double start, double end) {
        return this; // Return self
    }
}
