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
package com.ibm.mcp.jdtls;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Utility methods for resolving JDT elements from command arguments.
 */
public final class JdtUtils {

    private JdtUtils() {
    }

    /**
     * Resolve a type from command arguments.
     *
     * <p>Supports two argument formats:</p>
     * <ul>
     *   <li>[uri, line, character] - resolve type at position</li>
     *   <li>[fullyQualifiedName] - resolve by fully qualified name</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static IType resolveType(List<Object> arguments, IProgressMonitor monitor) throws JavaModelException {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        Object firstArg = arguments.get(0);

        if (firstArg instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) firstArg;
            String uri = (String) params.get("uri");
            if (uri != null && params.containsKey("line") && params.containsKey("character")) {
                int line = ((Number) params.get("line")).intValue();
                int character = ((Number) params.get("character")).intValue();
                return resolveTypeAtPosition(uri, line, character, monitor);
            }
            String fqn = (String) params.get("fullyQualifiedName");
            if (fqn != null) {
                return resolveTypeByName(fqn);
            }
        }

        if (firstArg instanceof String) {
            return resolveTypeByName((String) firstArg);
        }

        return null;
    }

    /**
     * Resolve a type at a position in a file using codeSelect.
     */
    public static IType resolveTypeAtPosition(String uri, int line, int character, IProgressMonitor monitor)
            throws JavaModelException {
        ICompilationUnit cu = getCompilationUnit(uri);
        if (cu == null) {
            return null;
        }

        int offset = getOffset(cu, line, character);
        IJavaElement[] elements = cu.codeSelect(offset, 0);
        for (IJavaElement element : elements) {
            if (element instanceof IType) {
                return (IType) element;
            }
            IType enclosingType = (IType) element.getAncestor(IJavaElement.TYPE);
            if (enclosingType != null) {
                return enclosingType;
            }
        }
        return null;
    }

    /**
     * Resolve a type by its fully qualified name across all Java projects.
     */
    public static IType resolveTypeByName(String fullyQualifiedName) throws JavaModelException {
        var projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (var project : projects) {
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject != null && javaProject.exists()) {
                IType type = javaProject.findType(fullyQualifiedName);
                if (type != null && type.exists()) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Get the ICompilationUnit for a file URI.
     */
    public static ICompilationUnit getCompilationUnit(String uri) {
        URI fileUri = URI.create(uri);
        IFile[] files = ResourcesPlugin.getWorkspace().getRoot()
                .findFilesForLocationURI(fileUri);
        if (files.length == 0) {
            return null;
        }
        IJavaElement element = JavaCore.create(files[0]);
        if (element instanceof ICompilationUnit) {
            return (ICompilationUnit) element;
        }
        return null;
    }

    /**
     * Convert line/character (0-based) to offset in a compilation unit.
     */
    public static int getOffset(ICompilationUnit cu, int line, int character) throws JavaModelException {
        String source = cu.getSource();
        if (source == null) {
            return 0;
        }
        int offset = 0;
        int currentLine = 0;
        for (int i = 0; i < source.length(); i++) {
            if (currentLine == line) {
                return offset + character;
            }
            if (source.charAt(i) == '\n') {
                currentLine++;
            }
            offset++;
        }
        return offset;
    }

    /**
     * Convert an offset in source to line/character (0-based) and put them
     * directly into the target map, replacing any "offset" key.
     */
    public static void putPosition(Map<String, Object> target, String source, int offset) {
        int line = 0;
        int character = 0;
        if (source != null) {
            for (int i = 0; i < Math.min(offset, source.length()); i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                    character = 0;
                } else {
                    character++;
                }
            }
        }
        target.put("line", line);
        target.put("character", character);
    }

    /**
     * Get the source code content from a workspace resource (IFile → ICompilationUnit).
     */
    public static String getSource(IResource resource) {
        if (resource instanceof IFile file) {
            IJavaElement element = JavaCore.create(file);
            if (element instanceof ICompilationUnit cu) {
                try {
                    return cu.getSource();
                } catch (JavaModelException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
