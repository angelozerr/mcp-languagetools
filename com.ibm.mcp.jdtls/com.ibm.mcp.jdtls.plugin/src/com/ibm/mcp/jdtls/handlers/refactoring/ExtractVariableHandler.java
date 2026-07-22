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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Statement;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.extractVariable" command.
 *
 * <p>Arguments: [{uri, startLine, startCharacter, endLine, endCharacter, variableName}]</p>
 *
 * <p>Extracts the selected expression into a local variable. The expression type is
 * determined from ITypeBinding. A local variable declaration is created with the
 * expression as the initializer, and the original expression is replaced with
 * the variable name reference.</p>
 *
 * <p>This is a best-effort implementation. It handles the common case where a single
 * expression is selected. Complex expressions spanning multiple statements or
 * partial expressions are not supported.</p>
 */
public class ExtractVariableHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String variableName = (String) params.get("variableName");

        if (uri == null || variableName == null || variableName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and variableName");
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
        String typeName = "var";
        ITypeBinding typeBinding = selectedExpression.resolveTypeBinding();
        if (typeBinding != null) {
            typeName = typeBinding.getName();
            if (typeName == null || typeName.isEmpty()) {
                typeName = "var";
            }
        }

        // Find the enclosing statement to insert the variable declaration before it
        Statement enclosingStatement = findEnclosingStatement(selectedExpression);
        if (enclosingStatement == null) {
            return createErrorResult("Cannot find enclosing statement for the expression");
        }

        // Get the expression text
        int exprStart = selectedExpression.getStartPosition();
        int exprEnd = exprStart + selectedExpression.getLength();
        String expressionText = source.substring(exprStart, exprEnd);

        // Determine indentation from the enclosing statement
        int stmtStart = enclosingStatement.getStartPosition();
        int lineStart = source.lastIndexOf('\n', stmtStart - 1) + 1;
        String indent = "";
        int idx = lineStart;
        while (idx < source.length() && (source.charAt(idx) == ' ' || source.charAt(idx) == '\t')) {
            idx++;
        }
        indent = source.substring(lineStart, idx);

        // Build the variable declaration
        String varDecl = indent + typeName + " " + variableName + " = " + expressionText + ";\n";

        // Build new source: insert variable declaration before the statement,
        // and replace the expression with the variable name
        StringBuilder newSource = new StringBuilder();
        newSource.append(source, 0, stmtStart);
        newSource.append(varDecl);
        newSource.append(source, stmtStart, exprStart);
        newSource.append(variableName);
        newSource.append(source.substring(exprEnd));

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
                        // Accept the deepest match or the exact match
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
     * Find the nearest enclosing Statement for a given AST node.
     */
    private Statement findEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof Statement) {
                return (Statement) current;
            }
            current = current.getParent();
        }
        return null;
    }
}
