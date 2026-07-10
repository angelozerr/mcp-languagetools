package com.redhat.mcp.languagetools.installer;

import com.redhat.mcp.languagetools.progress.AbstractProgressMonitor;
import com.redhat.mcp.languagetools.trace.TraceCollector;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Progress monitor that sends traces via TraceCollector.
 */
public class TraceProgressMonitor extends AbstractProgressMonitor {
    private final TraceCollector traceCollector;
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private volatile String currentText = "";

    /**
     * Create trace progress monitor with default total of 100.
     */
    public TraceProgressMonitor(TraceCollector traceCollector) {
        this(traceCollector, 100.0);
    }

    /**
     * Create trace progress monitor with specific total.
     */
    public TraceProgressMonitor(TraceCollector traceCollector, double total) {
        super(total);
        this.traceCollector = traceCollector;
    }

    @Override
    public void reportProgress(double progress, String message) {
        setCurrent(progress);
        this.currentText = message;
        // Note: trace updates are handled by wrappers (e.g., ProgressMonitorWrapper in DownloadTask)
        // This just updates the internal state for getFraction() which is used by the badge
    }

    @Override
    public void reportProgress(String message) {
        this.currentText = message;
        if (traceCollector != null) {
            traceCollector.info(message);
        }
    }

    @Override
    public void setComplete() {
        setCurrent(total);
    }

    /**
     * Get the current progress fraction (0.0 to 1.0).
     * This is used by the UI to display a visual progress bar.
     */
    public double getFraction() {
        return total > 0 ? current / total : 0;
    }


    @Override
    public boolean isCancelled() {
        return canceled.get();
    }

    @Override
    public void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Installation cancelled");
        }
    }

    @Override
    public <T> CompletableFuture<T> executeWithCancellation(CompletableFuture<T> future) {
        // Simple implementation: just return the future
        // Cancellation is handled via checkCancelled() calls
        return future;
    }

    @Override
    public boolean isSupported() {
        return traceCollector != null;
    }

    /**
     * Cancels the operation.
     */
    public void cancel() {
        canceled.set(true);
    }
}
