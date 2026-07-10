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

/**
 * Abstract base class for ProgressMonitor implementations.
 * Provides common functionality for tracking total and current progress.
 */
public abstract class AbstractProgressMonitor implements ProgressMonitor {

    protected final double total;
    protected volatile double current = 0;

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
}
