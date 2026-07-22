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
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.inlineVariable" command.
 *
 * <p>Arguments: [{uri, line, character}]</p>
 *
 * <p>Inlines a local variable by:
 * <ol>
 *   <li>Getting the variable's initializer expression</li>
 *   <li>Finding all references to the variable in the enclosing method</li>
 *   <li>Replacing each reference with the initializer expression</li>
 *   <li>Removing the variable declaration statement</li>
 * </ol>
 * </p>
 *
 * <p>This works for local variables with simple initializers. Variables without
 * initializers cannot be inlined. If the initializer has side effects, inlining
 * may change the program semantics when the variable is referenced multiple times.</p>
 */
public class InlineVariableHandler extends AbstractRefactoringHandler {

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

        ILocalVariable localVar = null;
        for (IJavaElement element : elements) {
            if (element instanceof ILocalVariable) {
                localVar = (ILocalVariable) element;
                break;
            }
        }

        if (localVar == null) {
            return createErrorResult("No local variable found at position");
        }

        String varName = localVar.getElementName();
        CompilationUnit ast = parseAST(cu, monitor);
        String source = cu.getSource();

        // Find the VariableDeclarationFragment for this variable
        VariableDeclarationFragment declFragment = findDeclarationFragment(ast, varName, offset);
        if (declFragment == null) {
            return createErrorResult("Cannot find variable declaration in AST");
        }

        Expression initializer = declFragment.getInitializer();
        if (initializer == null) {
            return createErrorResult("Variable has no initializer - cannot inline");
        }

        String initializerText = source.substring(initializer.getStartPosition(),
                initializer.getStartPosition() + initializer.getLength());

        // Find the binding for the variable to match references
        IVariableBinding varBinding = declFragment.resolveBinding();
        if (varBinding == null) {
            return createErrorResult("Cannot resolve variable binding");
        }

        // Find the enclosing method to scope the search
        MethodDeclaration enclosingMethod = findEnclosingMethod(declFragment);
        if (enclosingMethod == null) {
            return createErrorResult("Cannot find enclosing method");
        }

        // Find all references to the variable (excluding the declaration)
        List<int[]> references = new ArrayList<>();
        enclosingMethod.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node.getIdentifier().equals(varName) && node != declFragment.getName()) {
                    IBinding binding = node.resolveBinding();
                    if (binding instanceof IVariableBinding) {
                        IVariableBinding vb = (IVariableBinding) binding;
                        if (vb.isEqualTo(varBinding)) {
                            references.add(new int[] { node.getStartPosition(), node.getLength() });
                        }
                    }
                }
                return true;
            }
        });

        if (references.isEmpty()) {
            // No references found - just remove the declaration
        }

        // Find the declaration statement to remove
        ASTNode declParent = declFragment.getParent();
        int declStmtStart = -1;
        int declStmtEnd = -1;
        if (declParent instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement declStmt = (VariableDeclarationStatement) declParent;
            @SuppressWarnings("unchecked")
            List<VariableDeclarationFragment> fragments = declStmt.fragments();
            if (fragments.size() == 1) {
                // Simple case: remove the entire statement
                declStmtStart = declStmt.getStartPosition();
                declStmtEnd = declStmtStart + declStmt.getLength();
                // Include trailing newline
                if (declStmtEnd < source.length() && source.charAt(declStmtEnd) == '\n') {
                    declStmtEnd++;
                }
                // Include leading whitespace on the same line
                int lineStart = source.lastIndexOf('\n', declStmtStart - 1) + 1;
                boolean allWhitespace = true;
                for (int i = lineStart; i < declStmtStart; i++) {
                    if (source.charAt(i) != ' ' && source.charAt(i) != '\t') {
                        allWhitespace = false;
                        break;
                    }
                }
                if (allWhitespace) {
                    declStmtStart = lineStart;
                }
            }
        }

        // Apply replacements from end to start
        // First, replace all references, then remove the declaration
        references.sort((a, b) -> b[0] - a[0]);

        StringBuilder newSource = new StringBuilder(source);

        // Replace references with initializer text
        for (int[] ref : references) {
            // Wrap initializer in parentheses if it contains operators
            String replacement = needsParentheses(initializerText)
                    ? "(" + initializerText + ")"
                    : initializerText;
            newSource.replace(ref[0], ref[0] + ref[1], replacement);
        }

        // Remove the declaration statement
        if (declStmtStart >= 0 && declStmtEnd >= 0) {
            // Recalculate positions after reference replacements
            // Since we process from end to start, the declaration position is still valid
            // only if it appears before all references. Typically it does.
            newSource.delete(declStmtStart, declStmtEnd);
        }

        String result = newSource.toString();
        if (result.equals(source)) {
            return createSuccessResult(List.of());
        }

        List<Map<String, Object>> edits = createWholeFileEdit(uri, source, result);
        return createSuccessResult(edits);
    }

    /**
     * Find the VariableDeclarationFragment for the variable at the given offset.
     */
    private VariableDeclarationFragment findDeclarationFragment(CompilationUnit ast, String varName, int offset) {
        final VariableDeclarationFragment[] result = new VariableDeclarationFragment[1];
        ast.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (node.getName().getIdentifier().equals(varName)) {
                    int start = node.getStartPosition();
                    int end = start + node.getLength();
                    // Accept if the offset is within or near the declaration
                    if (result[0] == null || Math.abs(start - offset) < Math.abs(result[0].getStartPosition() - offset)) {
                        result[0] = node;
                    }
                }
                return true;
            }
        });
        return result[0];
    }

    /**
     * Find the enclosing MethodDeclaration for a given node.
     */
    private MethodDeclaration findEnclosingMethod(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration) {
                return (MethodDeclaration) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Determine if the initializer text needs to be wrapped in parentheses when inlined.
     */
    private boolean needsParentheses(String text) {
        // Simple heuristic: wrap in parens if it contains binary operators
        return text.contains("+") || text.contains("-") || text.contains("*")
                || text.contains("/") || text.contains("&&") || text.contains("||")
                || text.contains("?") || text.contains("^") || text.contains("|")
                || text.contains("&");
    }
}
