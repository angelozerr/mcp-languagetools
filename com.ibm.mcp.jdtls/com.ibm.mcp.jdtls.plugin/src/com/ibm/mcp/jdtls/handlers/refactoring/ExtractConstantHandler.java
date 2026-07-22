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

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.extractConstant" command.
 *
 * <p>Arguments: [{uri, startLine, startCharacter, endLine, endCharacter, constantName}]</p>
 *
 * <p>Similar to Extract Variable, but instead of creating a local variable, creates a
 * {@code private static final} field at the class level and replaces the selected
 * expression with a reference to the constant.</p>
 *
 * <p>The constant is placed as a field declaration at the beginning of the class body,
 * before any existing field or method declarations.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/ExtractConstantTool.java">javalens-mcp ExtractConstantTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class ExtractConstantHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String constantName = (String) params.get("constantName");

        if (uri == null || constantName == null || constantName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and constantName");
        }

        int startLine = ((Number) params.get("startLine")).intValue();
        int startCharacter = ((Number) params.get("startCharacter")).intValue();
        int endLine = ((Number) params.get("endLine")).intValue();
        int endCharacter = ((Number) params.get("endCharacter")).intValue();

        ICompilationUnit cu = JdtUtils.getCompilationUnit(uri);
        if (cu == null) {
            return createErrorResult("Compilation unit not found: " + uri);
        }

        String source = cu.getSource();
        int selStart = getOffset(source, startLine, startCharacter);
        int selEnd = getOffset(source, endLine, endCharacter);

        if (selStart >= selEnd) {
            return createErrorResult("Invalid selection range");
        }

        CompilationUnit ast = parseAST(cu, monitor);

        // Find the expression node at the selection
        Expression selectedExpression = findExpressionAt(ast, selStart, selEnd - selStart);
        if (selectedExpression == null) {
            return createErrorResult("No expression found at the selected position");
        }

        // Determine the expression type
        String typeName = "Object";
        ITypeBinding typeBinding = selectedExpression.resolveTypeBinding();
        if (typeBinding != null) {
            String resolvedName = typeBinding.getName();
            if (resolvedName != null && !resolvedName.isEmpty()) {
                typeName = resolvedName;
            }
        }

        // Find the enclosing type declaration
        TypeDeclaration enclosingType = findEnclosingType(selectedExpression);
        if (enclosingType == null) {
            return createErrorResult("Cannot find enclosing type declaration");
        }

        // Get the expression text
        int exprStart = selectedExpression.getStartPosition();
        int exprEnd = exprStart + selectedExpression.getLength();
        String expressionText = source.substring(exprStart, exprEnd);

        // Build the constant field declaration
        String constantDecl = "\n\tprivate static final " + typeName + " " + constantName
                + " = " + expressionText + ";\n";

        // Find position to insert the constant (after opening brace of the type)
        int typeBodyStart = findTypeBodyStart(source, enclosingType);
        if (typeBodyStart < 0) {
            return createErrorResult("Cannot determine type body start position");
        }

        // Build new source: insert constant declaration and replace expression
        StringBuilder newSource = new StringBuilder();

        if (exprStart > typeBodyStart) {
            // Constant insertion is before the expression replacement
            newSource.append(source, 0, typeBodyStart);
            newSource.append(constantDecl);
            newSource.append(source, typeBodyStart, exprStart);
            newSource.append(constantName);
            newSource.append(source.substring(exprEnd));
        } else {
            // Expression is before the constant insertion position (unlikely but handle it)
            newSource.append(source, 0, exprStart);
            newSource.append(constantName);
            newSource.append(source, exprEnd, typeBodyStart);
            newSource.append(constantDecl);
            newSource.append(source.substring(typeBodyStart));
        }

        List<Map<String, Object>> edits = createWholeFileEdit(uri, source, newSource.toString());
        return createSuccessResult(edits);
    }

    /**
     * Find the deepest Expression node covering the given range.
     */
    private Expression findExpressionAt(CompilationUnit ast, int offset, int length) {
        final Expression[] result = new Expression[1];
        ast.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (node instanceof Expression) {
                    int nodeStart = node.getStartPosition();
                    int nodeEnd = nodeStart + node.getLength();
                    if (nodeStart <= offset && nodeEnd >= offset + length) {
                        if (result[0] == null || (nodeStart >= result[0].getStartPosition()
                                && nodeEnd <= result[0].getStartPosition() + result[0].getLength())) {
                            result[0] = (Expression) node;
                        }
                    }
                }
            }
        });
        return result[0];
    }

    /**
     * Find the enclosing TypeDeclaration for a node.
     */
    private TypeDeclaration findEnclosingType(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof TypeDeclaration) {
                return (TypeDeclaration) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Find the position just after the opening brace of the type declaration.
     */
    private int findTypeBodyStart(String source, TypeDeclaration typeDecl) {
        int start = typeDecl.getStartPosition();
        int end = start + typeDecl.getLength();
        // Find the opening brace
        for (int i = start; i < end; i++) {
            if (source.charAt(i) == '{') {
                return i + 1;
            }
        }
        return -1;
    }
}
