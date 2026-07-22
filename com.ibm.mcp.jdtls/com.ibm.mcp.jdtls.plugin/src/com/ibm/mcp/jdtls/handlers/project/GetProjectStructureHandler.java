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
package com.ibm.mcp.jdtls.handlers.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import com.ibm.mcp.jdtls.ICommandHandler;

/**
 * Handler for "mcp.jdtls.getProjectStructure" command.
 *
 * <p>Arguments: none (uses first Java project in workspace)</p>
 *
 * <p>Enumerates source folders, packages, and compilation unit counts for the
 * project, providing a structural overview.</p>
 */
public class GetProjectStructureHandler implements ICommandHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        IJavaProject javaProject = findFirstJavaProject();
        if (javaProject == null) {
            return Map.of("error", "No Java project found in workspace");
        }

        List<Map<String, Object>> sourceFolders = new ArrayList<>();
        int totalPackages = 0;
        int totalFiles = 0;

        IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
        for (IPackageFragmentRoot root : roots) {
            if (monitor != null && monitor.isCanceled()) {
                break;
            }

            // Only process source folders (not libraries)
            if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
                continue;
            }

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("path", root.getPath().toString());

            List<Map<String, Object>> packages = new ArrayList<>();

            for (IJavaElement child : root.getChildren()) {
                if (child instanceof IPackageFragment) {
                    IPackageFragment pkg = (IPackageFragment) child;
                    ICompilationUnit[] cus = pkg.getCompilationUnits();

                    if (cus.length > 0 || !pkg.isDefaultPackage()) {
                        Map<String, Object> pkgInfo = new HashMap<>();
                        pkgInfo.put("name", pkg.isDefaultPackage() ? "(default)" : pkg.getElementName());
                        pkgInfo.put("fileCount", cus.length);
                        packages.add(pkgInfo);

                        totalPackages++;
                        totalFiles += cus.length;
                    }
                }
            }

            folderInfo.put("packages", packages);
            sourceFolders.add(folderInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("project", javaProject.getElementName());
        result.put("sourceFolders", sourceFolders);
        result.put("totalPackages", totalPackages);
        result.put("totalFiles", totalFiles);
        return result;
    }

    private IJavaProject findFirstJavaProject() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            try {
                if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
                    return JavaCore.create(project);
                }
            } catch (Exception e) {
                // Skip projects that cannot be processed
            }
        }
        return null;
    }
}
