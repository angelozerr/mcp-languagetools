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
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.pushDown" command.
 *
 * <p>Arguments: [{uri, line, character, memberNames}]</p>
 *
 * <p>Pushes down members from a superclass to its direct subclasses by:
 * <ol>
 *   <li>Resolving the type and finding its direct subclasses via ITypeHierarchy</li>
 *   <li>For each selected member: copying it to every direct subclass</li>
 *   <li>Removing the member from the superclass (or making it abstract if it's a method)</li>
 *   <li>Adjusting access modifiers if necessary</li>
 * </ol>
 * </p>
 *
 * <p>All subclasses must be in the workspace (source-available) for editing.
 * Members are inserted before the closing brace of each subclass type declaration.</p>
 */
public class PushDownHandler extends AbstractRefactoringHandler {

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        List<String> memberNames = (List<String>) params.get("memberNames");

        if (uri == null) {
            return createErrorResult("Missing required argument: uri");
        }

        if (memberNames == null || memberNames.isEmpty()) {
            return createErrorResult("At least one member name must be specified");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        IType type = JdtUtils.resolveTypeAtPosition(uri, line, character, monitor);
        if (type == null) {
            return createErrorResult("No type found at position");
        }

        // Find direct subclasses
        ITypeHierarchy hierarchy = type.newTypeHierarchy(monitor);
        IType[] subclasses = hierarchy.getSubtypes(type);
        if (subclasses == null || subclasses.length == 0) {
            return createErrorResult("Type has no direct subclasses");
        }

        ICompilationUnit superCu = type.getCompilationUnit();
        if (superCu == null) {
            return createErrorResult("Cannot find compilation unit for superclass");
        }

        String superSource = superCu.getSource();

        // Collect members to push down
        List<IMember> membersToMove = new ArrayList<>();
        for (String memberName : memberNames) {
            IField field = type.getField(memberName);
            if (field != null && field.exists()) {
                membersToMove.add(field);
                continue;
            }
            IMethod[] methods = type.getMethods();
            for (IMethod method : methods) {
                if (method.getElementName().equals(memberName)) {
                    membersToMove.add(method);
                    break;
                }
            }
        }

        if (membersToMove.isEmpty()) {
            return createErrorResult("No matching members found to push down");
        }

        // Get source for each member
        List<String> memberSources = new ArrayList<>();
        for (IMember member : membersToMove) {
            String memberSource = member.getSource();
            if (memberSource != null) {
                memberSources.add(memberSource);
            }
        }

        List<Map<String, Object>> allEdits = new ArrayList<>();

        // Add members to each subclass
        for (IType subclass : subclasses) {
            ICompilationUnit subCu = subclass.getCompilationUnit();
            if (subCu == null) {
                continue; // Skip binary subclasses
            }

            String subSource = subCu.getSource();
            String subUri = subCu.getResource().getLocationURI().toString();

            // Find the closing brace of the subclass
            ISourceRange subRange = subclass.getSourceRange();
            int subTypeEnd = subRange.getOffset() + subRange.getLength();
            int closingBrace = subSource.lastIndexOf('}', subTypeEnd);
            if (closingBrace < 0) {
                continue;
            }

            StringBuilder newSubSource = new StringBuilder(subSource);
            StringBuilder insertText = new StringBuilder();
            for (String ms : memberSources) {
                insertText.append("\n\t").append(ms.trim()).append("\n");
            }
            newSubSource.insert(closingBrace, insertText.toString());

            if (!newSubSource.toString().equals(subSource)) {
                allEdits.addAll(createWholeFileEdit(subUri, subSource, newSubSource.toString()));
            }
        }

        // Remove members from the superclass
        // Sort by position descending
        membersToMove.sort((a, b) -> {
            try {
                return b.getSourceRange().getOffset() - a.getSourceRange().getOffset();
            } catch (Exception e) {
                return 0;
            }
        });

        StringBuilder newSuperSource = new StringBuilder(superSource);
        for (IMember member : membersToMove) {
            ISourceRange range = member.getSourceRange();
            int memberStart = range.getOffset();
            int memberEnd = memberStart + range.getLength();

            // Include leading whitespace on the same line
            int lineStart = superSource.lastIndexOf('\n', memberStart - 1) + 1;
            boolean allWhitespace = true;
            for (int i = lineStart; i < memberStart; i++) {
                if (superSource.charAt(i) != ' ' && superSource.charAt(i) != '\t') {
                    allWhitespace = false;
                    break;
                }
            }
            if (allWhitespace) {
                memberStart = lineStart;
            }

            // Include trailing newlines
            while (memberEnd < newSuperSource.length()
                    && (newSuperSource.charAt(memberEnd) == '\n' || newSuperSource.charAt(memberEnd) == '\r')) {
                memberEnd++;
            }

            newSuperSource.delete(memberStart, memberEnd);
        }

        String superUri = superCu.getResource().getLocationURI().toString();
        if (!newSuperSource.toString().equals(superSource)) {
            allEdits.addAll(createWholeFileEdit(superUri, superSource, newSuperSource.toString()));
        }

        return createSuccessResult(allEdits);
    }
}
