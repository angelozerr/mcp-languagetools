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
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.encapsulateField" command.
 *
 * <p>Arguments: [{uri, line, character, getterName (optional), setterName (optional)}]</p>
 *
 * <p>Encapsulates a field by:
 * <ol>
 *   <li>Generating a getter method ({@code getFieldName()} or {@code isFieldName()} for boolean)</li>
 *   <li>Generating a setter method ({@code setFieldName(value)})</li>
 *   <li>Making the field private if not already</li>
 *   <li>Finding all direct field accesses using SearchEngine</li>
 *   <li>Replacing reads with getter calls and writes with setter calls</li>
 * </ol>
 * </p>
 *
 * <p>Call site replacement distinguishes read vs. write access based on the SearchMatch
 * flags. Custom getter and setter names can be specified via arguments.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/EncapsulateFieldTool.java">javalens-mcp EncapsulateFieldTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class EncapsulateFieldHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");

        if (uri == null) {
            return createErrorResult("Missing required argument: uri");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        ICompilationUnit cu = JdtUtils.getCompilationUnit(uri);
        if (cu == null) {
            return createErrorResult("Compilation unit not found: " + uri);
        }

        int offset = JdtUtils.getOffset(cu, line, character);
        IJavaElement[] elements = cu.codeSelect(offset, 0);

        IField field = null;
        for (IJavaElement element : elements) {
            if (element instanceof IField) {
                field = (IField) element;
                break;
            }
        }

        if (field == null) {
            return createErrorResult("No field found at position");
        }

        String fieldName = field.getElementName();
        String fieldType = Signature.toString(field.getTypeSignature());
        boolean isBoolean = "boolean".equals(fieldType) || "Boolean".equals(fieldType);

        // Determine getter and setter names
        String capitalizedName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String getterName = (String) params.getOrDefault("getterName",
                isBoolean ? "is" + capitalizedName : "get" + capitalizedName);
        String setterName = (String) params.getOrDefault("setterName", "set" + capitalizedName);

        IType declaringType = field.getDeclaringType();
        ICompilationUnit declCu = declaringType.getCompilationUnit();
        if (declCu == null) {
            return createErrorResult("Cannot find compilation unit for field declaration");
        }

        String source = declCu.getSource();
        CompilationUnit ast = parseAST(declCu, monitor);

        // Find the field declaration in AST
        FieldInfo fieldInfo = findFieldDeclaration(ast, fieldName, source);
        if (fieldInfo == null) {
            return createErrorResult("Cannot find field declaration in AST");
        }

        // Build getter and setter methods
        String getterMethod = buildGetter(fieldName, fieldType, getterName);
        String setterMethod = buildSetter(fieldName, fieldType, setterName);

        // Find the end of the field declaration to insert methods after it
        int insertPos = fieldInfo.declarationEnd;
        // Skip to end of line
        while (insertPos < source.length() && source.charAt(insertPos) != '\n') {
            insertPos++;
        }
        if (insertPos < source.length()) {
            insertPos++; // Skip the newline
        }

        // Make field private if not already
        StringBuilder newSource = new StringBuilder(source);

        // Modify field visibility to private
        if (!Flags.isPrivate(field.getFlags())) {
            String fieldDeclText = source.substring(fieldInfo.declarationStart, fieldInfo.declarationEnd);
            String newFieldDecl = fieldDeclText;
            if (fieldDeclText.contains("public ")) {
                newFieldDecl = fieldDeclText.replace("public ", "private ");
            } else if (fieldDeclText.contains("protected ")) {
                newFieldDecl = fieldDeclText.replace("protected ", "private ");
            } else if (!fieldDeclText.contains("private ")) {
                // Package-private: add private
                // Find position after any annotations and modifiers
                int typePos = fieldDeclText.indexOf(fieldType);
                if (typePos > 0) {
                    newFieldDecl = fieldDeclText.substring(0, typePos) + "private "
                            + fieldDeclText.substring(typePos);
                } else {
                    newFieldDecl = "private " + fieldDeclText.trim();
                }
            }
            newSource.replace(fieldInfo.declarationStart, fieldInfo.declarationEnd, newFieldDecl);
            // Adjust insertPos if the declaration length changed
            int delta = newFieldDecl.length() - fieldDeclText.length();
            insertPos += delta;
        }

        // Insert getter and setter methods
        String methodsToInsert = "\n" + getterMethod + "\n" + setterMethod;
        newSource.insert(insertPos, methodsToInsert);

        List<Map<String, Object>> allEdits = new ArrayList<>();
        String declUri = declCu.getResource().getLocationURI().toString();

        if (!newSource.toString().equals(source)) {
            allEdits.addAll(createWholeFileEdit(declUri, source, newSource.toString()));
        }

        // Find all field references in other files and replace with getter/setter calls
        SearchPattern readPattern = SearchPattern.createPattern(
                field,
                IJavaSearchConstants.READ_ACCESSES);
        SearchPattern writePattern = SearchPattern.createPattern(
                field,
                IJavaSearchConstants.WRITE_ACCESSES);

        if (readPattern != null) {
            IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
            Map<ICompilationUnit, List<int[]>> readSites = new HashMap<>();

            SearchEngine engine = new SearchEngine();
            engine.search(
                    readPattern,
                    new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                    scope,
                    new SearchRequestor() {
                        @Override
                        public void acceptSearchMatch(SearchMatch match) {
                            if (match.getElement() instanceof IJavaElement) {
                                ICompilationUnit matchCu = (ICompilationUnit) ((IJavaElement) match.getElement())
                                        .getAncestor(IJavaElement.COMPILATION_UNIT);
                                if (matchCu != null && !matchCu.equals(declCu)) {
                                    readSites.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                            .add(new int[] { match.getOffset(), match.getLength() });
                                }
                            }
                        }
                    },
                    monitor);

            for (Map.Entry<ICompilationUnit, List<int[]>> entry : readSites.entrySet()) {
                ICompilationUnit readCu = entry.getKey();
                List<int[]> positions = entry.getValue();
                String readSource = readCu.getSource();
                String readUri = readCu.getResource().getLocationURI().toString();

                positions.sort((a, b) -> b[0] - a[0]);
                StringBuilder readNewSource = new StringBuilder(readSource);
                for (int[] pos : positions) {
                    readNewSource.replace(pos[0], pos[0] + pos[1], getterName + "()");
                }

                String result = readNewSource.toString();
                if (!result.equals(readSource)) {
                    allEdits.addAll(createWholeFileEdit(readUri, readSource, result));
                }
            }
        }

        if (writePattern != null) {
            IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
            Map<ICompilationUnit, List<int[]>> writeSites = new HashMap<>();

            SearchEngine engine = new SearchEngine();
            engine.search(
                    writePattern,
                    new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                    scope,
                    new SearchRequestor() {
                        @Override
                        public void acceptSearchMatch(SearchMatch match) {
                            if (match.getElement() instanceof IJavaElement) {
                                ICompilationUnit matchCu = (ICompilationUnit) ((IJavaElement) match.getElement())
                                        .getAncestor(IJavaElement.COMPILATION_UNIT);
                                if (matchCu != null && !matchCu.equals(declCu)) {
                                    writeSites.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                            .add(new int[] { match.getOffset(), match.getLength() });
                                }
                            }
                        }
                    },
                    monitor);

            // Write access replacement is more complex - need to handle "field = value"
            // For simplicity, we replace "field" with "setFieldName" and note the user
            // may need to manually adjust the assignment syntax
            for (Map.Entry<ICompilationUnit, List<int[]>> entry : writeSites.entrySet()) {
                ICompilationUnit writeCu = entry.getKey();
                List<int[]> positions = entry.getValue();
                String writeSource = writeCu.getSource();
                String writeUri = writeCu.getResource().getLocationURI().toString();

                // Check if we already have edits for this file from read replacements
                // For simplicity, skip write sites in files we already modified
                // A more sophisticated implementation would merge edits

                positions.sort((a, b) -> b[0] - a[0]);
                StringBuilder writeNewSource = new StringBuilder(writeSource);
                for (int[] pos : positions) {
                    // Find the assignment: "field = value" -> "setField(value)"
                    int assignIdx = writeSource.indexOf('=', pos[0] + pos[1]);
                    if (assignIdx >= 0 && assignIdx < pos[0] + pos[1] + 10) {
                        // Find the end of the assignment value (semicolon)
                        int semiIdx = writeSource.indexOf(';', assignIdx);
                        if (semiIdx >= 0) {
                            String value = writeSource.substring(assignIdx + 1, semiIdx).trim();
                            String replacement = setterName + "(" + value + ")";
                            writeNewSource.replace(pos[0], semiIdx, replacement);
                        }
                    }
                }

                String result = writeNewSource.toString();
                if (!result.equals(writeSource)) {
                    allEdits.addAll(createWholeFileEdit(writeUri, writeSource, result));
                }
            }
        }

        return createSuccessResult(allEdits);
    }

    private String buildGetter(String fieldName, String fieldType, String getterName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\tpublic ").append(fieldType).append(" ").append(getterName).append("() {\n");
        sb.append("\t\treturn ").append(fieldName).append(";\n");
        sb.append("\t}\n");
        return sb.toString();
    }

    private String buildSetter(String fieldName, String fieldType, String setterName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\tpublic void ").append(setterName).append("(").append(fieldType).append(" ").append(fieldName).append(") {\n");
        sb.append("\t\tthis.").append(fieldName).append(" = ").append(fieldName).append(";\n");
        sb.append("\t}\n");
        return sb.toString();
    }

    private FieldInfo findFieldDeclaration(CompilationUnit ast, String fieldName, String source) {
        final FieldInfo[] result = new FieldInfo[1];
        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                @SuppressWarnings("unchecked")
                List<VariableDeclarationFragment> fragments = node.fragments();
                for (VariableDeclarationFragment frag : fragments) {
                    if (frag.getName().getIdentifier().equals(fieldName)) {
                        result[0] = new FieldInfo(
                                node.getStartPosition(),
                                node.getStartPosition() + node.getLength());
                        return false;
                    }
                }
                return true;
            }
        });
        return result[0];
    }

    private static class FieldInfo {
        final int declarationStart;
        final int declarationEnd;

        FieldInfo(int start, int end) {
            this.declarationStart = start;
            this.declarationEnd = end;
        }
    }
}
