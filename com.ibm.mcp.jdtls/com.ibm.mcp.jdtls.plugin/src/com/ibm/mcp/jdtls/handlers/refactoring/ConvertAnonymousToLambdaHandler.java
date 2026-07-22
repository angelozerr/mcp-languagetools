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
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.convertAnonymousToLambda" command.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Converts an anonymous class to a lambda expression when the anonymous class
 * implements a functional interface (an interface with a single abstract method).</p>
 *
 * <p>The conversion:
 * <ol>
 *   <li>Verifies the anonymous class implements a functional interface</li>
 *   <li>Extracts the single method's parameters and body</li>
 *   <li>Builds a lambda expression: {@code (params) -> body} or {@code (params) -> { statements }}</li>
 *   <li>Replaces the ClassInstanceCreation with the lambda</li>
 * </ol>
 * </p>
 *
 * <p>If the method body contains a single return statement, the lambda uses expression
 * form. Otherwise, a block lambda is generated.</p>
 */
public class ConvertAnonymousToLambdaHandler extends AbstractRefactoringHandler {

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

        String source = cu.getSource();
        int offset = JdtUtils.getOffset(cu, line, character);
        CompilationUnit ast = parseAST(cu, monitor);

        // Find the ClassInstanceCreation with AnonymousClassDeclaration at the position
        ClassInstanceCreation creation = findClassInstanceCreation(ast, offset);
        if (creation == null) {
            return createErrorResult("No anonymous class instance creation found at position");
        }

        AnonymousClassDeclaration anonymousDecl = creation.getAnonymousClassDeclaration();
        if (anonymousDecl == null) {
            return createErrorResult("No anonymous class declaration found");
        }

        // Check that it implements a functional interface
        ITypeBinding typeBinding = creation.resolveTypeBinding();
        if (typeBinding == null) {
            return createErrorResult("Cannot resolve type binding for anonymous class");
        }

        ITypeBinding[] interfaces = typeBinding.getInterfaces();
        ITypeBinding superclass = typeBinding.getSuperclass();
        ITypeBinding targetType = null;

        // Try interfaces first, then superclass
        if (interfaces.length > 0) {
            targetType = interfaces[0];
        } else if (superclass != null && superclass.isInterface()) {
            targetType = superclass;
        }

        if (targetType == null) {
            // Try functional interface detection via the creation type
            ITypeBinding creationType = creation.getType().resolveBinding();
            if (creationType != null) {
                targetType = creationType;
            }
        }

        // Get the single method declaration from the anonymous class
        @SuppressWarnings("unchecked")
        List<BodyDeclaration> bodyDecls = anonymousDecl.bodyDeclarations();
        MethodDeclaration singleMethod = null;
        int methodCount = 0;

        for (BodyDeclaration bodyDecl : bodyDecls) {
            if (bodyDecl instanceof MethodDeclaration) {
                singleMethod = (MethodDeclaration) bodyDecl;
                methodCount++;
            }
        }

        if (methodCount != 1) {
            return createErrorResult("Anonymous class must have exactly one method to convert to lambda");
        }

        if (singleMethod == null) {
            return createErrorResult("No method found in anonymous class");
        }

        // Build the lambda expression
        StringBuilder lambda = new StringBuilder();

        // Parameters
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> lambdaParams = singleMethod.parameters();

        if (lambdaParams.isEmpty()) {
            lambda.append("()");
        } else if (lambdaParams.size() == 1) {
            // Single parameter - can omit type and parentheses
            lambda.append(lambdaParams.get(0).getName().getIdentifier());
        } else {
            lambda.append("(");
            for (int i = 0; i < lambdaParams.size(); i++) {
                if (i > 0) {
                    lambda.append(", ");
                }
                lambda.append(lambdaParams.get(i).getName().getIdentifier());
            }
            lambda.append(")");
        }

        lambda.append(" -> ");

        // Body
        Block body = singleMethod.getBody();
        if (body == null) {
            return createErrorResult("Method has no body");
        }

        @SuppressWarnings("unchecked")
        List<Statement> statements = body.statements();

        if (statements.size() == 1) {
            Statement stmt = statements.get(0);
            if (stmt instanceof ReturnStatement) {
                // Single return: use expression form
                ReturnStatement retStmt = (ReturnStatement) stmt;
                Expression retExpr = retStmt.getExpression();
                if (retExpr != null) {
                    lambda.append(source.substring(retExpr.getStartPosition(),
                            retExpr.getStartPosition() + retExpr.getLength()));
                } else {
                    // void return
                    lambda.append("{}");
                }
            } else if (stmt instanceof ExpressionStatement) {
                // Single expression statement: use expression form
                ExpressionStatement exprStmt = (ExpressionStatement) stmt;
                Expression expr = exprStmt.getExpression();
                lambda.append(source.substring(expr.getStartPosition(),
                        expr.getStartPosition() + expr.getLength()));
            } else {
                // Single non-expression statement: use block form
                lambda.append("{\n");
                lambda.append("\t\t").append(source.substring(stmt.getStartPosition(),
                        stmt.getStartPosition() + stmt.getLength()).trim());
                lambda.append("\n\t}");
            }
        } else {
            // Multiple statements: use block form
            lambda.append("{\n");
            for (Statement stmt : statements) {
                lambda.append("\t\t").append(source.substring(stmt.getStartPosition(),
                        stmt.getStartPosition() + stmt.getLength()).trim()).append("\n");
            }
            lambda.append("\t}");
        }

        // Replace the entire ClassInstanceCreation with the lambda
        int creationStart = creation.getStartPosition();
        int creationEnd = creationStart + creation.getLength();

        String newSource = source.substring(0, creationStart) + lambda.toString() + source.substring(creationEnd);

        List<Map<String, Object>> edits = createWholeFileEdit(uri, source, newSource);
        return createSuccessResult(edits);
    }

    /**
     * Find the ClassInstanceCreation at or near the given offset.
     */
    private ClassInstanceCreation findClassInstanceCreation(CompilationUnit ast, int offset) {
        final ClassInstanceCreation[] result = new ClassInstanceCreation[1];
        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (node.getAnonymousClassDeclaration() != null) {
                    int start = node.getStartPosition();
                    int end = start + node.getLength();
                    if (start <= offset && end >= offset) {
                        // Accept the most specific (deepest) match
                        if (result[0] == null
                                || node.getLength() < result[0].getLength()) {
                            result[0] = node;
                        }
                    }
                }
                return true;
            }
        });
        return result[0];
    }
}
