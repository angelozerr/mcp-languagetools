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
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.extractSuperclass" command.
 *
 * <p>Arguments: [{uri, line, character, superclassName, memberNames}]</p>
 *
 * <p>Extracts a superclass from a class by:
 * <ol>
 *   <li>Creating a new abstract superclass file with selected members</li>
 *   <li>Changing the original class to extend the new superclass</li>
 *   <li>Removing the moved members from the original class</li>
 * </ol>
 * </p>
 *
 * <p>The new superclass file is created in the same package as the original class.
 * Moved methods retain their bodies. Moved fields retain their initializers.
 * Access modifiers are adjusted from private to protected where necessary.</p>
 */
public class ExtractSuperclassHandler extends AbstractRefactoringHandler {

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String superclassName = (String) params.get("superclassName");
        List<String> memberNames = (List<String>) params.get("memberNames");

        if (uri == null || superclassName == null || superclassName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and superclassName");
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

        ICompilationUnit cu = type.getCompilationUnit();
        if (cu == null) {
            return createErrorResult("Compilation unit not found for type");
        }

        String source = cu.getSource();
        IPackageFragment pkg = type.getPackageFragment();
        String packageName = pkg.getElementName();

        // Collect members to move
        List<IMember> membersToMove = new ArrayList<>();
        for (String memberName : memberNames) {
            // Check fields first
            IField field = type.getField(memberName);
            if (field != null && field.exists()) {
                membersToMove.add(field);
                continue;
            }
            // Check methods
            IMethod[] methods = type.getMethods();
            for (IMethod method : methods) {
                if (method.getElementName().equals(memberName)) {
                    membersToMove.add(method);
                    break;
                }
            }
        }

        if (membersToMove.isEmpty()) {
            return createErrorResult("No matching members found to extract");
        }

        // Build the superclass source
        StringBuilder superSource = new StringBuilder();
        if (!packageName.isEmpty()) {
            superSource.append("package ").append(packageName).append(";\n\n");
        }
        superSource.append("public abstract class ").append(superclassName).append(" {\n\n");

        // Add each member to the superclass
        for (IMember member : membersToMove) {
            String memberSource = member.getSource();
            if (memberSource != null) {
                // Adjust private to protected
                String adjusted = memberSource.replaceFirst("\\bprivate\\b", "protected");
                superSource.append("\t").append(adjusted.trim()).append("\n\n");
            }
        }
        superSource.append("}\n");

        // Remove moved members from the original class and add extends clause
        // Sort members by source position in reverse order to safely remove from end to start
        membersToMove.sort((a, b) -> {
            try {
                return b.getSourceRange().getOffset() - a.getSourceRange().getOffset();
            } catch (Exception e) {
                return 0;
            }
        });

        StringBuilder newSource = new StringBuilder(source);

        for (IMember member : membersToMove) {
            ISourceRange range = member.getSourceRange();
            int memberStart = range.getOffset();
            int memberEnd = memberStart + range.getLength();

            // Include any trailing whitespace/newline
            while (memberEnd < newSource.length()
                    && (newSource.charAt(memberEnd) == '\n' || newSource.charAt(memberEnd) == '\r')) {
                memberEnd++;
            }

            newSource.delete(memberStart, memberEnd);
        }

        // Add "extends superclassName" to the class declaration
        String classKeyword = "class " + type.getElementName();
        int classIdx = newSource.indexOf(classKeyword);
        if (classIdx >= 0) {
            int afterClassName = classIdx + classKeyword.length();
            String rest = newSource.substring(afterClassName);
            if (rest.trim().startsWith("extends")) {
                // Already extends something - cannot extract superclass
                return createErrorResult("Class already extends another class");
            }
            newSource.insert(afterClassName, " extends " + superclassName);
        }

        // Build the superclass file URI
        String originalUri = cu.getResource().getLocationURI().toString();
        String superclassUri = originalUri.substring(0, originalUri.lastIndexOf('/') + 1)
                + superclassName + ".java";

        // Create edits for both files
        List<Map<String, Object>> edits = new ArrayList<>();

        // Edit 1: Modify original class
        edits.addAll(createWholeFileEdit(uri, source, newSource.toString()));

        // Edit 2: Create the new superclass file
        edits.add(Map.of(
                "uri", superclassUri,
                "range", createRange(0, 0, 0, 0),
                "newText", superSource.toString()));

        return createSuccessResult(edits);
    }
}
