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
package com.ibm.mcp.jdtls.handlers.navigation;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Handler for "mcp.jdtls.getEnclosingElement" command.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Resolves the element at the given position, then navigates up the parent
 * chain to return the enclosing method, type, and package.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/GetEnclosingElementTool.java">javalens-mcp GetEnclosingElementTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class GetEnclosingElementHandler extends AbstractPositionHandler {

    @Override
    protected Object handleElements(IJavaElement[] elements, ICompilationUnit cu, int offset,
            IProgressMonitor monitor) throws Exception {
        if (elements.length == 0) {
            return Map.of("error", "No element found at position");
        }

        IJavaElement element = elements[0];
        Map<String, Object> result = new HashMap<>();

        // Find enclosing method
        IMethod enclosingMethod = (IMethod) element.getAncestor(IJavaElement.METHOD);
        if (enclosingMethod == null && element instanceof IMethod) {
            enclosingMethod = (IMethod) element;
        }
        if (enclosingMethod != null) {
            Map<String, Object> methodInfo = new HashMap<>();
            methodInfo.put("name", enclosingMethod.getElementName());
            methodInfo.put("line", getElementLine(enclosingMethod, cu));
            result.put("enclosingMethod", methodInfo);
        } else {
            result.put("enclosingMethod", null);
        }

        // Find enclosing type
        IType enclosingType = (IType) element.getAncestor(IJavaElement.TYPE);
        if (enclosingType == null && element instanceof IType) {
            enclosingType = (IType) element;
        }
        if (enclosingType != null) {
            Map<String, Object> typeInfo = new HashMap<>();
            typeInfo.put("name", enclosingType.getElementName());
            typeInfo.put("fullyQualifiedName", enclosingType.getFullyQualifiedName());
            typeInfo.put("kind", getTypeKind(enclosingType));
            result.put("enclosingType", typeInfo);
        } else {
            result.put("enclosingType", null);
        }

        // Find enclosing package
        IPackageDeclaration[] packages = cu.getPackageDeclarations();
        if (packages.length > 0) {
            Map<String, Object> packageInfo = new HashMap<>();
            packageInfo.put("name", packages[0].getElementName());
            result.put("enclosingPackage", packageInfo);
        } else {
            Map<String, Object> packageInfo = new HashMap<>();
            packageInfo.put("name", "(default package)");
            result.put("enclosingPackage", packageInfo);
        }

        return result;
    }

    private int getElementLine(IJavaElement element, ICompilationUnit cu) {
        try {
            if (element instanceof ISourceReference sourceRef) {
                ISourceRange range = sourceRef.getSourceRange();
                if (range != null) {
                    String source = cu.getSource();
                    if (source != null) {
                        return offsetToLine(source, range.getOffset());
                    }
                }
            }
        } catch (JavaModelException e) {
            // Ignore
        }
        return -1;
    }

    private int offsetToLine(String source, int offset) {
        int line = 0;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String getTypeKind(IType type) throws JavaModelException {
        if (type.isAnnotation()) {
            return "annotation";
        } else if (type.isEnum()) {
            return "enum";
        } else if (type.isRecord()) {
            return "record";
        } else if (type.isInterface()) {
            return "interface";
        } else {
            return "class";
        }
    }
}
