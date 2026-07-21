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
package com.ibm.mcp.jdtls.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.ibm.mcp.jdtls.ICommandHandler;
import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.callHierarchyIncoming" and "mcp.jdtls.callHierarchyOutgoing" commands.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Returns the call hierarchy (callers or callees) for the method at the given position.</p>
 */
public class CallHierarchyHandler implements ICommandHandler {

    private final boolean incoming;

    public CallHierarchyHandler(boolean incoming) {
        this.incoming = incoming;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of("error", "Missing arguments");
        }

        Map<String, Object> params = (Map<String, Object>) arguments.get(0);
        String uri = (String) params.get("uri");
        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        ICompilationUnit cu = JdtUtils.getCompilationUnit(uri);
        if (cu == null) {
            return Map.of("error", "Compilation unit not found: " + uri);
        }

        int offset = JdtUtils.getOffset(cu, line, character);
        IJavaElement[] elements = cu.codeSelect(offset, 0);

        IMethod method = null;
        for (IJavaElement element : elements) {
            if (element instanceof IMethod) {
                method = (IMethod) element;
                break;
            }
        }

        if (method == null) {
            return Map.of("error", "No method found at position");
        }

        if (incoming) {
            return findCallers(method, monitor);
        } else {
            return findCallees(method, monitor);
        }
    }

    private Map<String, Object> findCallers(IMethod method, IProgressMonitor monitor) throws Exception {
        SearchPattern pattern = SearchPattern.createPattern(
                method,
                IJavaSearchConstants.REFERENCES);

        if (pattern == null) {
            return Map.of("method", method.getElementName(), "callers", List.of());
        }

        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        List<Map<String, Object>> callers = new ArrayList<>();

        SearchEngine engine = new SearchEngine();
        engine.search(
                pattern,
                new SearchParticipant[]{SearchEngine.getDefaultSearchParticipant()},
                scope,
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) {
                        Object element = match.getElement();
                        if (element instanceof IMember member) {
                            callers.add(formatMember(member, match));
                        }
                    }
                },
                monitor);

        Map<String, Object> result = new HashMap<>();
        result.put("method", method.getElementName());
        result.put("declaringType", method.getDeclaringType().getFullyQualifiedName());
        result.put("callers", callers);
        return result;
    }

    private Map<String, Object> findCallees(IMethod method, IProgressMonitor monitor) throws Exception {
        // TODO: Implement outgoing call hierarchy using AST visitor
        // For now, return a placeholder
        Map<String, Object> result = new HashMap<>();
        result.put("method", method.getElementName());
        result.put("declaringType", method.getDeclaringType().getFullyQualifiedName());
        result.put("callees", List.of());
        result.put("note", "Outgoing call hierarchy not yet implemented");
        return result;
    }

    private Map<String, Object> formatMember(IMember member, SearchMatch match) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", member.getElementName());
        if (member.getDeclaringType() != null) {
            info.put("declaringType", member.getDeclaringType().getFullyQualifiedName());
        }
        if (member.getResource() != null) {
            info.put("uri", member.getResource().getLocationURI().toString());
        }
        info.put("offset", match.getOffset());
        info.put("length", match.getLength());
        return info;
    }
}
