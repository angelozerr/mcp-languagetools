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
 * Progress monitoring and cancellation support for long-running operations.
 * Inspired by IntelliJ's ProgressIndicator API.
 *
 * This interface is independent of any specific protocol (MCP, LSP, etc.)
 * and can be adapted to different progress/cancellation mechanisms.
 */
public interface ProgressMonitor {

    /**
     * Report progress with a specific current value.
     * The total is defined at construction time.
     *
     * @param progress Current progress value
     * @param message Descriptive message for the user
     */
    void reportProgress(double progress, String message);

    /**
     * Report progress with an indeterminate amount (message only).
     *
     * @param message Descriptive message for the user
     */
    void reportProgress(String message);

    /**
     * Mark the operation as complete.
     */
    void setComplete();

    /**
     * Get the total expected progress value.
     *
     * @return The total progress value
     */
    double getTotal();

    /**
     * Check if the operation has been cancelled.
     *
     * @return true if cancelled, false otherwise
     */
    boolean isCancelled();

    /**
     * Check if cancelled and throw some cancel exception if so.
     */
    void checkCancelled();

    /**
     * Wrap a CompletableFuture to propagate cancellation.
     * If the operation is cancelled, the returned future will complete exceptionally
     * with CancellationException.
     *
     * @param future The future to wrap
     * @param <T> The future result type
     * @return A future that completes exceptionally if cancelled
     */
    <T> CompletableFuture<T> executeWithCancellation(CompletableFuture<T> future);

    /**
     * Check if progress reporting is supported/available.
     *
     * @return true if progress can be reported, false otherwise
     */
    boolean isSupported();

    /**
     * Create a no-op progress monitor that does nothing.
     * Useful for operations that don't need progress tracking.
     *
     * @return A no-op progress monitor instance
     */
    static ProgressMonitor none() {
        return NoOpProgressMonitor.INSTANCE;
    }

}
