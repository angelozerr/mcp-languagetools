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
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.inlineMethod" command.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Inlines a method by:
 * <ol>
 *   <li>Getting the method body from the declaration</li>
 *   <li>Finding all call sites using SearchEngine</li>
 *   <li>At each call site, replacing the method call with the method body,
 *       substituting parameter names with argument expressions</li>
 *   <li>Handling return statements by replacing them with the expression</li>
 * </ol>
 * </p>
 *
 * <p>This is a best-effort implementation. It handles simple methods with a single
 * return statement or methods that consist of a single expression. Complex methods
 * with multiple return paths, local variables that conflict with the call site scope,
 * or side effects in argument expressions are not fully handled.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/InlineMethodTool.java">javalens-mcp InlineMethodTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class InlineMethodHandler extends AbstractRefactoringHandler {

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

        IMethod method = null;
        for (IJavaElement element : elements) {
            if (element instanceof IMethod) {
                method = (IMethod) element;
                break;
            }
        }

        if (method == null) {
            return createErrorResult("No method found at position");
        }

        // Get the method declaration to extract the body
        ICompilationUnit declCu = method.getCompilationUnit();
        if (declCu == null) {
            return createErrorResult("Cannot find compilation unit for method declaration");
        }

        CompilationUnit declAst = parseAST(declCu, monitor);
        String declSource = declCu.getSource();

        // Find the MethodDeclaration AST node
        MethodDeclaration methodDecl = findMethodDeclaration(declAst, method);
        if (methodDecl == null) {
            return createErrorResult("Cannot find method declaration in AST");
        }

        Block body = methodDecl.getBody();
        if (body == null) {
            return createErrorResult("Method has no body (abstract or interface method)");
        }

        // Get parameter names
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> parameters = methodDecl.parameters();
        List<String> paramNames = new ArrayList<>();
        for (SingleVariableDeclaration param : parameters) {
            paramNames.add(param.getName().getIdentifier());
        }

        // Get the method body text
        @SuppressWarnings("unchecked")
        List<Statement> statements = body.statements();
        if (statements.isEmpty()) {
            return createErrorResult("Method body is empty");
        }

        // For simple inlining: extract the body as an expression or block
        String inlineText = null;
        boolean isSingleReturn = false;

        if (statements.size() == 1) {
            Statement stmt = statements.get(0);
            if (stmt instanceof ReturnStatement) {
                ReturnStatement ret = (ReturnStatement) stmt;
                Expression retExpr = ret.getExpression();
                if (retExpr != null) {
                    inlineText = declSource.substring(retExpr.getStartPosition(),
                            retExpr.getStartPosition() + retExpr.getLength());
                    isSingleReturn = true;
                }
            } else if (stmt instanceof ExpressionStatement) {
                ExpressionStatement exprStmt = (ExpressionStatement) stmt;
                inlineText = declSource.substring(exprStmt.getStartPosition(),
                        exprStmt.getStartPosition() + exprStmt.getLength());
                // Remove trailing semicolon
                if (inlineText.endsWith(";")) {
                    inlineText = inlineText.substring(0, inlineText.length() - 1);
                }
            }
        }

        if (inlineText == null) {
            // Multi-statement body: build a block
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (Statement stmt : statements) {
                String stmtText = declSource.substring(stmt.getStartPosition(),
                        stmt.getStartPosition() + stmt.getLength());
                sb.append("\t").append(stmtText.trim()).append("\n");
            }
            sb.append("}");
            inlineText = sb.toString();
        }

        // Find all call sites using SearchEngine
        SearchPattern pattern = SearchPattern.createPattern(
                method,
                IJavaSearchConstants.REFERENCES);

        if (pattern == null) {
            return createErrorResult("Cannot create search pattern for method");
        }

        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        Map<ICompilationUnit, List<int[]>> callSites = new HashMap<>();

        SearchEngine engine = new SearchEngine();
        final String finalInlineText = inlineText;
        final boolean finalIsSingleReturn = isSingleReturn;

        engine.search(
                pattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope,
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) {
                        if (match.getElement() instanceof IJavaElement) {
                            ICompilationUnit matchCu = (ICompilationUnit) ((IJavaElement) match.getElement())
                                    .getAncestor(IJavaElement.COMPILATION_UNIT);
                            if (matchCu != null) {
                                callSites.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                        .add(new int[] { match.getOffset(), match.getLength() });
                            }
                        }
                    }
                },
                monitor);

        if (callSites.isEmpty()) {
            return createErrorResult("No call sites found for method");
        }

        List<Map<String, Object>> allEdits = new ArrayList<>();

        for (Map.Entry<ICompilationUnit, List<int[]>> entry : callSites.entrySet()) {
            ICompilationUnit callCu = entry.getKey();
            List<int[]> positions = entry.getValue();
            String callSource = callCu.getSource();
            String callUri = callCu.getResource().getLocationURI().toString();

            CompilationUnit callAst = parseAST(callCu, monitor);

            // Process each call site - find the enclosing MethodInvocation
            // Sort from end to start to preserve offsets
            positions.sort((a, b) -> b[0] - a[0]);

            StringBuilder newCallSource = new StringBuilder(callSource);

            for (int[] pos : positions) {
                MethodInvocation invocation = findMethodInvocation(callAst, pos[0]);
                if (invocation == null) {
                    continue;
                }

                // Get argument expressions
                @SuppressWarnings("unchecked")
                List<Expression> args = invocation.arguments();

                // Substitute parameters with arguments in the inline text
                String substituted = finalInlineText;
                for (int i = 0; i < Math.min(paramNames.size(), args.size()); i++) {
                    String argText = callSource.substring(args.get(i).getStartPosition(),
                            args.get(i).getStartPosition() + args.get(i).getLength());
                    substituted = substituted.replace(paramNames.get(i), argText);
                }

                // Determine replacement range
                // For a single return expression, replace just the method invocation
                // For a block, we need to find the enclosing ExpressionStatement
                int replaceStart = invocation.getStartPosition();
                int replaceEnd = replaceStart + invocation.getLength();

                if (!finalIsSingleReturn) {
                    // Find enclosing statement and replace the whole statement
                    Statement enclosing = findEnclosingStatement(invocation);
                    if (enclosing != null) {
                        replaceStart = enclosing.getStartPosition();
                        replaceEnd = replaceStart + enclosing.getLength();
                    }
                }

                newCallSource.replace(replaceStart, replaceEnd, substituted);
            }

            String result = newCallSource.toString();
            if (!result.equals(callSource)) {
                allEdits.addAll(createWholeFileEdit(callUri, callSource, result));
            }
        }

        // Optionally remove the method declaration if all call sites are inlined
        // For now, keep the method declaration - the user can remove it separately

        return createSuccessResult(allEdits);
    }

    /**
     * Find the MethodDeclaration AST node for a given IMethod.
     */
    private MethodDeclaration findMethodDeclaration(CompilationUnit ast, IMethod method) {
        final MethodDeclaration[] result = new MethodDeclaration[1];
        try {
            ISourceRange nameRange = method.getNameRange();
            ast.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    SimpleName name = node.getName();
                    if (name.getStartPosition() == nameRange.getOffset()) {
                        result[0] = node;
                        return false;
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            // Fallback: match by name
            ast.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    if (node.getName().getIdentifier().equals(method.getElementName())) {
                        result[0] = node;
                        return false;
                    }
                    return true;
                }
            });
        }
        return result[0];
    }

    /**
     * Find the MethodInvocation at the given offset.
     */
    private MethodInvocation findMethodInvocation(CompilationUnit ast, int offset) {
        final MethodInvocation[] result = new MethodInvocation[1];
        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                int start = node.getStartPosition();
                int end = start + node.getLength();
                if (start <= offset && end >= offset) {
                    result[0] = node;
                }
                return true;
            }
        });
        return result[0];
    }

    /**
     * Find the nearest enclosing Statement for a given AST node.
     */
    private Statement findEnclosingStatement(org.eclipse.jdt.core.dom.ASTNode node) {
        org.eclipse.jdt.core.dom.ASTNode current = node;
        while (current != null) {
            if (current instanceof Statement) {
                return (Statement) current;
            }
            current = current.getParent();
        }
        return null;
    }
}
