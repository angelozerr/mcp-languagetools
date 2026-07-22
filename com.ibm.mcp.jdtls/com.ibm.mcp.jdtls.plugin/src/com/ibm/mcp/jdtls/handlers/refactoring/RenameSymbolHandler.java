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
package com.ibm.mcp.jdtls.handlers.refactoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.ReplaceEdit;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.renameSymbol" command.
 *
 * <p>Arguments: [{uri, line, character, newName}]</p>
 *
 * <p>Resolves the element at the given position via codeSelect, then finds all
 * references to the element across the workspace using SearchEngine. Builds text
 * edits to replace the old name with the new name at each occurrence.</p>
 *
 * <p>Supports renaming of types, methods, fields, local variables, and parameters.</p>
 */
public class RenameSymbolHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String newName = (String) params.get("newName");

        if (uri == null || newName == null || newName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and newName");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        ICompilationUnit cu = JdtUtils.getCompilationUnit(uri);
        if (cu == null) {
            return createErrorResult("Compilation unit not found: " + uri);
        }

        int offset = JdtUtils.getOffset(cu, line, character);
        IJavaElement[] elements = cu.codeSelect(offset, 0);
        if (elements == null || elements.length == 0) {
            return createErrorResult("No element found at position");
        }

        IJavaElement element = elements[0];
        String oldName = element.getElementName();

        if (oldName.equals(newName)) {
            return createErrorResult("New name is the same as the old name");
        }

        // For local variables, rename within the same compilation unit using AST
        if (element instanceof ILocalVariable) {
            return renameInSameUnit(cu, element, oldName, newName, monitor);
        }

        // For types, methods, fields - do workspace-wide rename via SearchEngine
        return renameWorkspaceWide(element, oldName, newName, monitor);
    }

    /**
     * Rename a local variable within its compilation unit using AST traversal.
     */
    private Map<String, Object> renameInSameUnit(ICompilationUnit cu, IJavaElement element,
            String oldName, String newName, IProgressMonitor monitor) throws Exception {
        CompilationUnit ast = parseAST(cu, monitor);
        String source = cu.getSource();
        String cuUri = cu.getResource().getLocationURI().toString();

        // Resolve the binding key for the element to match references accurately
        List<int[]> positions = new ArrayList<>();

        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node.getIdentifier().equals(oldName)) {
                    IBinding binding = node.resolveBinding();
                    if (binding != null) {
                        IJavaElement boundElement = binding.getJavaElement();
                        if (boundElement != null && boundElement.equals(element)) {
                            positions.add(new int[] { node.getStartPosition(), node.getLength() });
                        }
                    }
                }
                return true;
            }
        });

        if (positions.isEmpty()) {
            return createErrorResult("No references found for the element");
        }

        // Apply replacements from end to start to preserve offsets
        positions.sort((a, b) -> b[0] - a[0]);
        StringBuilder sb = new StringBuilder(source);
        for (int[] pos : positions) {
            sb.replace(pos[0], pos[0] + pos[1], newName);
        }

        String newSource = sb.toString();
        List<Map<String, Object>> edits = createWholeFileEdit(cuUri, source, newSource);
        return createSuccessResult(edits);
    }

    /**
     * Rename a type, method, or field across the workspace using SearchEngine.
     */
    private Map<String, Object> renameWorkspaceWide(IJavaElement element, String oldName,
            String newName, IProgressMonitor monitor) throws Exception {

        SearchPattern pattern = SearchPattern.createPattern(
                element,
                IJavaSearchConstants.ALL_OCCURRENCES);

        if (pattern == null) {
            return createErrorResult("Cannot create search pattern for element");
        }

        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        // Collect all matches grouped by compilation unit
        Map<ICompilationUnit, List<int[]>> matchesByCu = new HashMap<>();

        SearchEngine engine = new SearchEngine();
        engine.search(
                pattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope,
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) {
                        if (match.getResource() != null) {
                            Object matchElement = match.getElement();
                            if (matchElement instanceof IJavaElement) {
                                ICompilationUnit matchCu = (ICompilationUnit) ((IJavaElement) matchElement)
                                        .getAncestor(IJavaElement.COMPILATION_UNIT);
                                if (matchCu != null) {
                                    matchesByCu.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                            .add(new int[] { match.getOffset(), match.getLength() });
                                }
                            }
                        }
                    }
                },
                monitor);

        if (matchesByCu.isEmpty()) {
            return createErrorResult("No occurrences found");
        }

        List<Map<String, Object>> allEdits = new ArrayList<>();

        for (Map.Entry<ICompilationUnit, List<int[]>> entry : matchesByCu.entrySet()) {
            ICompilationUnit matchCu = entry.getKey();
            List<int[]> positions = entry.getValue();
            String source = matchCu.getSource();
            String matchUri = matchCu.getResource().getLocationURI().toString();

            // Sort positions from end to start
            positions.sort((a, b) -> b[0] - a[0]);

            StringBuilder sb = new StringBuilder(source);
            for (int[] pos : positions) {
                sb.replace(pos[0], pos[0] + pos[1], newName);
            }

            String newSource = sb.toString();
            if (!newSource.equals(source)) {
                allEdits.addAll(createWholeFileEdit(matchUri, source, newSource));
            }
        }

        return createSuccessResult(allEdits);
    }
}
