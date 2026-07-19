/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.languagetools.configuration;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Watches a single file for changes (create, modify, delete).
 * Handles the case where the file's parent directory does not exist yet
 * by watching the grandparent directory for the parent's creation.
 */
public class FileWatcher {

    private static final Logger LOG = Logger.getLogger(FileWatcher.class);

    private final Path fileToWatch;
    private final Path parentDir;
    private final Path grandParentDir;
    private final String targetFileName;
    private final String targetDirName;
    private final Runnable onChange;

    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean running = false;
    private volatile boolean watchingParent = false;
    private WatchKey currentKey;

    public FileWatcher(Path fileToWatch, Runnable onChange) {
        this.fileToWatch = fileToWatch;
        this.parentDir = fileToWatch.getParent();
        this.grandParentDir = parentDir != null ? parentDir.getParent() : null;
        this.targetFileName = fileToWatch.getFileName().toString();
        this.targetDirName = parentDir != null ? parentDir.getFileName().toString() : null;
        this.onChange = onChange;
    }

    public void start() {
        if (running) {
            return;
        }

        try {
            running = true;
            watchService = FileSystems.getDefault().newWatchService();
            executorService = Executors.newSingleThreadExecutor();

            registerWatch();

            executorService.submit(this::watchLoop);
            LOG.infof("Started watching: %s", fileToWatch);
        } catch (IOException e) {
            running = false;
            LOG.warnf(e, "Failed to start file watcher for: %s", fileToWatch);
        }
    }

    public void stop() {
        running = false;

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.debugf("Error closing watch service: %s", e.getMessage());
            }
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }

        LOG.infof("Stopped watching: %s", fileToWatch);
    }

    private void registerWatch() throws IOException {
        if (currentKey != null) {
            currentKey.cancel();
            currentKey = null;
        }

        if (Files.exists(parentDir)) {
            currentKey = parentDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchingParent = true;
            LOG.debugf("Watching parent directory: %s", parentDir);
        } else if (grandParentDir != null && Files.exists(grandParentDir)) {
            currentKey = grandParentDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchingParent = false;
            LOG.debugf("Watching grandparent directory: %s (waiting for %s)", grandParentDir, targetDirName);
        } else {
            watchingParent = false;
            LOG.debugf("Neither parent nor grandparent directory exists for: %s", fileToWatch);
        }
    }

    private void watchLoop() {
        try {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    break;
                }

                boolean needsReRegister = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    String eventFileName = ev.context().toString();

                    if (!watchingParent) {
                        // Watching grandparent — waiting for parent dir to appear
                        if (eventFileName.equals(targetDirName)
                                && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            LOG.debugf("Target directory created: %s", targetDirName);
                            needsReRegister = true;
                            if (Files.exists(fileToWatch)) {
                                fireOnChange();
                            }
                        }
                    } else {
                        // Watching parent — looking for file events
                        if (eventFileName.equals(targetFileName)) {
                            fireOnChange();
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE
                                && eventFileName.equals(targetDirName)) {
                            // Parent directory itself was deleted
                            needsReRegister = true;
                            fireOnChange();
                        }
                    }
                }

                boolean valid = key.reset();

                if (needsReRegister || !valid) {
                    try {
                        Thread.sleep(100);
                        registerWatch();
                    } catch (IOException e) {
                        LOG.debugf("Failed to re-register watch: %s", e.getMessage());
                        if (!valid) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (ClosedWatchServiceException e) {
            // Normal shutdown
        } catch (Exception e) {
            if (running) {
                LOG.errorf(e, "Error in file watch loop for: %s", fileToWatch);
            }
        }
    }

    private void fireOnChange() {
        LOG.infof("File change detected: %s", fileToWatch);
        try {
            onChange.run();
        } catch (Exception e) {
            LOG.errorf(e, "Error in file change callback for: %s", fileToWatch);
        }
    }
}
