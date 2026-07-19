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
package com.ibm.mcp.languagetools.lsp;

import com.ibm.mcp.languagetools.configuration.FileWatcher;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Watches instance file for changes: ${workspace}/.lsp-servers/{serverType}.json
 * Triggers callbacks when the instance appears, changes, or disappears.
 * Delegates to {@link FileWatcher} for the actual file watching.
 */
public class InstanceFileWatcher {

    private static final Logger LOG = Logger.getLogger(InstanceFileWatcher.class);

    private final String workspacePath;
    private final String serverType;
    private final Consumer<LspInstanceRegistry.InstanceInfo> onInstanceChanged;
    private final Runnable onInstanceRemoved;

    private FileWatcher fileWatcher;

    public InstanceFileWatcher(String workspacePath, String serverType,
                               Consumer<LspInstanceRegistry.InstanceInfo> onInstanceChanged,
                               Runnable onInstanceRemoved) {
        this.workspacePath = workspacePath;
        this.serverType = serverType;
        this.onInstanceChanged = onInstanceChanged;
        this.onInstanceRemoved = onInstanceRemoved;
    }

    public void start() {
        Path instanceFile = LspInstanceRegistry.getInstanceFilePath(workspacePath, serverType);
        fileWatcher = new FileWatcher(instanceFile, this::handleFileChanged);
        fileWatcher.start();
        LOG.infof("Watching instance file: %s", instanceFile);
    }

    public void stop() {
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
        LOG.infof("Stopped watching instance file for %s", serverType);
    }

    private void handleFileChanged() {
        Path instanceFile = LspInstanceRegistry.getInstanceFilePath(workspacePath, serverType);
        if (Files.exists(instanceFile)) {
            LspInstanceRegistry.InstanceInfo newInstance = LspInstanceRegistry.findInstance(workspacePath, serverType);
            if (newInstance != null) {
                LOG.infof("New/updated instance detected: %s", newInstance);
                try {
                    onInstanceChanged.accept(newInstance);
                } catch (Exception e) {
                    LOG.errorf(e, "Error in instance changed callback");
                }
            } else {
                LOG.debugf("Instance file changed but no valid instance found");
                handleInstanceRemoved();
            }
        } else {
            handleInstanceRemoved();
        }
    }

    private void handleInstanceRemoved() {
        LOG.infof("Instance removed or became invalid for %s", serverType);
        try {
            onInstanceRemoved.run();
        } catch (Exception e) {
            LOG.errorf(e, "Error in instance removed callback");
        }
    }
}
