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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.moveTypeToNewFile" command.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Moves a nested or inner type to a new file by:
 * <ol>
 *   <li>Extracting the type declaration from the enclosing file</li>
 *   <li>Creating a new file with the type as a top-level type</li>
 *   <li>Removing the type from the original file</li>
 *   <li>Updating imports in both files</li>
 * </ol>
 * </p>
 *
 * <p>The type must be a nested/inner type (not already a top-level type). The new file
 * is created in the same package. Static keyword is removed if present (since the type
 * becomes top-level). Imports from the original file that are used by the moved type
 * are copied to the new file.</p>
 */
public class MoveTypeToNewFileHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");

        if (uri == null) {
            return createErrorResult("Missing required argument: uri");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        IType type = JdtUtils.resolveTypeAtPosition(uri, line, character, monitor);
        if (type == null) {
            return createErrorResult("No type found at position");
        }

        // Check that this is a nested/inner type
        IType declaringType = type.getDeclaringType();
        if (declaringType == null) {
            return createErrorResult("Type is already a top-level type - cannot move to new file");
        }

        ICompilationUnit cu = type.getCompilationUnit();
        if (cu == null) {
            return createErrorResult("Cannot find compilation unit for type");
        }

        String source = cu.getSource();
        CompilationUnit ast = parseAST(cu, monitor);

        IPackageFragment pkg = type.getPackageFragment();
        String packageName = pkg.getElementName();
        String typeName = type.getElementName();

        // Find the TypeDeclaration in the AST
        TypeDeclaration typeDecl = findTypeDeclaration(ast, typeName);
        if (typeDecl == null) {
            return createErrorResult("Cannot find type declaration in AST");
        }

        // Get the type source
        int typeStart = typeDecl.getStartPosition();
        int typeEnd = typeStart + typeDecl.getLength();
        String typeSource = source.substring(typeStart, typeEnd);

        // Remove "static" modifier if present (becoming top-level)
        typeSource = typeSource.replaceFirst("\\bstatic\\s+", "");

        // Ensure it's public
        if (!typeSource.startsWith("public")) {
            typeSource = typeSource.replaceFirst("(private|protected|)\\s*(class|interface|enum|@interface)",
                    "public $2");
        }

        // Collect type references used in the moved type to determine needed imports
        Set<String> usedTypeNames = new HashSet<>();
        typeDecl.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleType node) {
                Name name = node.getName();
                ITypeBinding binding = node.resolveBinding();
                if (binding != null) {
                    String qualifiedName = binding.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.contains(".")
                            && !qualifiedName.startsWith("java.lang.")) {
                        usedTypeNames.add(qualifiedName);
                    }
                }
                return true;
            }
        });

        // Build the new file source
        StringBuilder newFileSource = new StringBuilder();
        if (!packageName.isEmpty()) {
            newFileSource.append("package ").append(packageName).append(";\n\n");
        }

        // Add needed imports
        @SuppressWarnings("unchecked")
        List<ImportDeclaration> existingImports = ast.imports();
        for (ImportDeclaration imp : existingImports) {
            String importName = imp.getName().getFullyQualifiedName();
            // Include the import if it matches any used type
            boolean needed = false;
            for (String usedType : usedTypeNames) {
                if (usedType.equals(importName) || usedType.startsWith(importName + ".")) {
                    needed = true;
                    break;
                }
                if (imp.isOnDemand()) {
                    String pkg2 = importName;
                    if (usedType.startsWith(pkg2 + ".")) {
                        needed = true;
                        break;
                    }
                }
            }
            if (needed) {
                String impSource = source.substring(imp.getStartPosition(),
                        imp.getStartPosition() + imp.getLength());
                newFileSource.append(impSource).append("\n");
            }
        }

        // Add import for the enclosing type if the moved type references it
        String enclosingFqn = declaringType.getFullyQualifiedName();
        if (usedTypeNames.contains(enclosingFqn)) {
            newFileSource.append("import ").append(enclosingFqn).append(";\n");
        }

        if (!usedTypeNames.isEmpty()) {
            newFileSource.append("\n");
        }

        newFileSource.append(typeSource).append("\n");

        // Remove the type from the original file
        // Also remove any trailing whitespace/newlines
        int removeStart = typeStart;
        int removeEnd = typeEnd;

        // Include leading whitespace on the same line
        int lineStart = source.lastIndexOf('\n', removeStart - 1) + 1;
        boolean allWhitespace = true;
        for (int i = lineStart; i < removeStart; i++) {
            if (source.charAt(i) != ' ' && source.charAt(i) != '\t') {
                allWhitespace = false;
                break;
            }
        }
        if (allWhitespace) {
            removeStart = lineStart;
        }

        // Include trailing newlines
        while (removeEnd < source.length()
                && (source.charAt(removeEnd) == '\n' || source.charAt(removeEnd) == '\r')) {
            removeEnd++;
        }

        String newOriginalSource = source.substring(0, removeStart) + source.substring(removeEnd);

        // Build edits
        List<Map<String, Object>> edits = new ArrayList<>();

        // Edit 1: Remove type from original file
        edits.addAll(createWholeFileEdit(uri, source, newOriginalSource));

        // Edit 2: Create new file
        String newFileUri = uri.substring(0, uri.lastIndexOf('/') + 1) + typeName + ".java";
        edits.add(Map.of(
                "uri", newFileUri,
                "range", createRange(0, 0, 0, 0),
                "newText", newFileSource.toString()));

        return createSuccessResult(edits);
    }

    /**
     * Find a TypeDeclaration by name in the AST. Searches nested types as well.
     */
    private TypeDeclaration findTypeDeclaration(CompilationUnit ast, String typeName) {
        final TypeDeclaration[] result = new TypeDeclaration[1];
        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                if (node.getName().getIdentifier().equals(typeName)) {
                    // Prefer nested types over top-level types
                    if (result[0] == null || node.getParent() instanceof TypeDeclaration) {
                        result[0] = node;
                    }
                }
                return true;
            }
        });
        return result[0];
    }
}
