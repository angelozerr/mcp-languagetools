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
package com.ibm.mcp.jdtls.handlers.quality;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.ibm.mcp.jdtls.ICommandHandler;

/**
 * Handler for "mcp.jdtls.findLargeClasses" command.
 *
 * <p>Arguments: [{maxMethods (optional, default 20), maxFields (optional, default 20),
 * maxLoc (optional, default 500)}]</p>
 *
 * <p>Scans all CUs in the workspace and filters types exceeding any threshold.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/FindLargeClassesTool.java">javalens-mcp FindLargeClassesTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class FindLargeClassesHandler implements ICommandHandler {

    private static final int DEFAULT_MAX_METHODS = 20;
    private static final int DEFAULT_MAX_FIELDS = 20;
    private static final int DEFAULT_MAX_LOC = 500;

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        int maxMethods = DEFAULT_MAX_METHODS;
        int maxFields = DEFAULT_MAX_FIELDS;
        int maxLoc = DEFAULT_MAX_LOC;

        if (arguments != null && !arguments.isEmpty()) {
            Map<String, Object> params = (Map<String, Object>) arguments.get(0);
            if (params.containsKey("maxMethods")) {
                maxMethods = ((Number) params.get("maxMethods")).intValue();
            }
            if (params.containsKey("maxFields")) {
                maxFields = ((Number) params.get("maxFields")).intValue();
            }
            if (params.containsKey("maxLoc")) {
                maxLoc = ((Number) params.get("maxLoc")).intValue();
            }
        }

        final int thresholdMethods = maxMethods;
        final int thresholdFields = maxFields;
        final int thresholdLoc = maxLoc;

        List<Map<String, Object>> largeClasses = new ArrayList<>();

        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            if (monitor != null && monitor.isCanceled()) {
                break;
            }
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null || !javaProject.exists()) {
                continue;
            }
            for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
                if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
                    continue;
                }
                for (IJavaElement child : root.getChildren()) {
                    if (monitor != null && monitor.isCanceled()) {
                        break;
                    }
                    if (child instanceof IPackageFragment pkg) {
                        for (ICompilationUnit cu : pkg.getCompilationUnits()) {
                            analyzeCompilationUnit(cu, thresholdMethods, thresholdFields,
                                    thresholdLoc, largeClasses, monitor);
                        }
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("largeClasses", largeClasses);
        result.put("count", largeClasses.size());
        return result;
    }

    private void analyzeCompilationUnit(ICompilationUnit cu, int thresholdMethods,
            int thresholdFields, int thresholdLoc, List<Map<String, Object>> largeClasses,
            IProgressMonitor monitor) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit ast = (CompilationUnit) parser.createAST(monitor);

        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                int methodCount = 0;
                int fieldCount = 0;

                for (Object bodyDecl : node.bodyDeclarations()) {
                    if (bodyDecl instanceof MethodDeclaration) {
                        methodCount++;
                    } else if (bodyDecl instanceof FieldDeclaration fd) {
                        fieldCount += fd.fragments().size();
                    }
                }

                int startLine = ast.getLineNumber(node.getStartPosition());
                int endLine = ast.getLineNumber(node.getStartPosition() + node.getLength());
                int loc = endLine - startLine + 1;

                if (methodCount > thresholdMethods || fieldCount > thresholdFields
                        || loc > thresholdLoc) {
                    Map<String, Object> classInfo = new HashMap<>();
                    classInfo.put("name", node.getName().getIdentifier());
                    if (node.resolveBinding() != null) {
                        classInfo.put("fqn", node.resolveBinding().getQualifiedName());
                    }
                    if (cu.getResource() != null) {
                        classInfo.put("uri", cu.getResource().getLocationURI().toString());
                    }
                    classInfo.put("methods", methodCount);
                    classInfo.put("fields", fieldCount);
                    classInfo.put("loc", loc);
                    largeClasses.add(classInfo);
                }

                return true;
            }
        });
    }
}
