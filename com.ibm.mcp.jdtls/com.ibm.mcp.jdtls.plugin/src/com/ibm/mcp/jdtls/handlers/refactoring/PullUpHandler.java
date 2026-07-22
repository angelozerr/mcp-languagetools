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
 * Handler for "mcp.jdtls.pullUp" command.
 *
 * <p>Arguments: [{uri, line, character, memberNames}]</p>
 *
 * <p>Pulls up members from a subclass to its superclass by:
 * <ol>
 *   <li>Resolving the type and finding its superclass via the type hierarchy</li>
 *   <li>For each selected member: copying it to the superclass</li>
 *   <li>Removing the member from the subclass</li>
 *   <li>Adjusting access modifiers (private becomes protected)</li>
 * </ol>
 * </p>
 *
 * <p>The superclass must be in the workspace (source-available) for editing.
 * Members are inserted before the closing brace of the superclass type declaration.</p>
 */
public class PullUpHandler extends AbstractRefactoringHandler {

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

        // Find the superclass
        ITypeHierarchy hierarchy = type.newSupertypeHierarchy(monitor);
        IType superclass = hierarchy.getSuperclass(type);
        if (superclass == null) {
            return createErrorResult("Type has no superclass");
        }

        ICompilationUnit superCu = superclass.getCompilationUnit();
        if (superCu == null) {
            return createErrorResult("Superclass is not in the workspace (binary type)");
        }

        ICompilationUnit subCu = type.getCompilationUnit();
        if (subCu == null) {
            return createErrorResult("Cannot find compilation unit for subclass");
        }

        String subSource = subCu.getSource();
        String superSource = superCu.getSource();

        // Collect members to pull up
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
            return createErrorResult("No matching members found to pull up");
        }

        // Build member sources for the superclass (adjusting visibility)
        List<String> memberSources = new ArrayList<>();
        for (IMember member : membersToMove) {
            String memberSource = member.getSource();
            if (memberSource != null) {
                // Change private to protected
                String adjusted = memberSource.replaceFirst("\\bprivate\\b", "protected");
                memberSources.add(adjusted);
            }
        }

        // Insert members into the superclass before the closing brace
        int superTypeEnd = superclass.getSourceRange().getOffset() + superclass.getSourceRange().getLength();
        // Find the closing brace of the superclass type
        int closingBrace = superSource.lastIndexOf('}', superTypeEnd);
        if (closingBrace < 0) {
            return createErrorResult("Cannot find closing brace of superclass");
        }

        StringBuilder newSuperSource = new StringBuilder(superSource);
        StringBuilder insertText = new StringBuilder();
        for (String ms : memberSources) {
            insertText.append("\n\t").append(ms.trim()).append("\n");
        }
        newSuperSource.insert(closingBrace, insertText.toString());

        // Remove members from the subclass
        // Sort by position descending
        membersToMove.sort((a, b) -> {
            try {
                return b.getSourceRange().getOffset() - a.getSourceRange().getOffset();
            } catch (Exception e) {
                return 0;
            }
        });

        StringBuilder newSubSource = new StringBuilder(subSource);
        for (IMember member : membersToMove) {
            ISourceRange range = member.getSourceRange();
            int memberStart = range.getOffset();
            int memberEnd = memberStart + range.getLength();

            // Include leading whitespace and trailing newline
            int lineStart = subSource.lastIndexOf('\n', memberStart - 1) + 1;
            boolean allWhitespace = true;
            for (int i = lineStart; i < memberStart; i++) {
                if (subSource.charAt(i) != ' ' && subSource.charAt(i) != '\t') {
                    allWhitespace = false;
                    break;
                }
            }
            if (allWhitespace) {
                memberStart = lineStart;
            }
            while (memberEnd < newSubSource.length()
                    && (newSubSource.charAt(memberEnd) == '\n' || newSubSource.charAt(memberEnd) == '\r')) {
                memberEnd++;
            }

            newSubSource.delete(memberStart, memberEnd);
        }

        // Build edits
        List<Map<String, Object>> edits = new ArrayList<>();
        String subUri = subCu.getResource().getLocationURI().toString();
        String superUri = superCu.getResource().getLocationURI().toString();

        if (!newSubSource.toString().equals(subSource)) {
            edits.addAll(createWholeFileEdit(subUri, subSource, newSubSource.toString()));
        }
        if (!newSuperSource.toString().equals(superSource)) {
            edits.addAll(createWholeFileEdit(superUri, superSource, newSuperSource.toString()));
        }

        return createSuccessResult(edits);
    }
}
