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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.extractMethod" command.
 *
 * <p>Arguments: [{uri, startLine, startCharacter, endLine, endCharacter, methodName}]</p>
 *
 * <p>Extracts the selected statements into a new private method. Performs variable
 * analysis to determine:
 * <ul>
 *   <li>Parameters: variables defined before the selection but used inside</li>
 *   <li>Return value: variable defined inside the selection but used after</li>
 *   <li>Local variables: variables used only within the selection</li>
 * </ul>
 * </p>
 *
 * <p>This is a best-effort implementation handling common scenarios. Complex cases
 * such as multiple return values, control flow statements (break, continue) spanning
 * the selection boundary, or exception handling may not be fully supported.</p>
 */
public class ExtractMethodHandler extends AbstractRefactoringHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String methodName = (String) params.get("methodName");

        if (uri == null || methodName == null || methodName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and methodName");
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

        // Find the enclosing method declaration
        ASTNode coveringNode = findCoveringNode(ast, selStart, selEnd - selStart);
        MethodDeclaration enclosingMethod = findEnclosingMethod(coveringNode);
        if (enclosingMethod == null) {
            return createErrorResult("Selection must be within a method body");
        }

        TypeDeclaration enclosingType = findEnclosingType(enclosingMethod);
        if (enclosingType == null) {
            return createErrorResult("Cannot find enclosing type declaration");
        }

        // Find all statements within the selection
        Block body = enclosingMethod.getBody();
        if (body == null) {
            return createErrorResult("Enclosing method has no body");
        }

        @SuppressWarnings("unchecked")
        List<Statement> allStatements = body.statements();
        List<Statement> selectedStatements = new ArrayList<>();
        for (Statement stmt : allStatements) {
            int stmtStart = stmt.getStartPosition();
            int stmtEnd = stmtStart + stmt.getLength();
            if (stmtStart >= selStart && stmtEnd <= selEnd) {
                selectedStatements.add(stmt);
            }
        }

        if (selectedStatements.isEmpty()) {
            return createErrorResult("No complete statements found in selection");
        }

        // Analyze variables
        Set<IVariableBinding> definedBefore = new LinkedHashSet<>();
        Set<IVariableBinding> usedInSelection = new LinkedHashSet<>();
        Set<IVariableBinding> definedInSelection = new LinkedHashSet<>();
        Set<IVariableBinding> usedAfter = new LinkedHashSet<>();

        // Collect variables defined before the selection
        int firstSelStart = selectedStatements.get(0).getStartPosition();
        for (Statement stmt : allStatements) {
            if (stmt.getStartPosition() < firstSelStart) {
                collectDefinedVariables(stmt, definedBefore);
            }
        }

        // Collect variables used and defined in the selection
        for (Statement stmt : selectedStatements) {
            collectUsedVariables(stmt, usedInSelection);
            collectDefinedVariables(stmt, definedInSelection);
        }

        // Collect variables used after the selection
        Statement lastSelected = selectedStatements.get(selectedStatements.size() - 1);
        int afterSelEnd = lastSelected.getStartPosition() + lastSelected.getLength();
        for (Statement stmt : allStatements) {
            if (stmt.getStartPosition() >= afterSelEnd) {
                collectUsedVariables(stmt, usedAfter);
            }
        }

        // Parameters: defined before selection and used in selection
        List<IVariableBinding> parameters = new ArrayList<>();
        for (IVariableBinding var : usedInSelection) {
            if (definedBefore.contains(var)) {
                parameters.add(var);
            }
        }

        // Return variable: defined in selection and used after
        IVariableBinding returnVar = null;
        for (IVariableBinding var : definedInSelection) {
            if (usedAfter.contains(var)) {
                returnVar = var;
                break; // Only support single return value
            }
        }

        // Build the new method source
        AST ast2 = ast.getAST();
        boolean isStatic = Modifier.isStatic(enclosingMethod.getModifiers());
        String returnType = returnVar != null ? returnVar.getType().getName() : "void";

        StringBuilder newMethod = new StringBuilder();
        newMethod.append("\n\n\tprivate ");
        if (isStatic) {
            newMethod.append("static ");
        }
        newMethod.append(returnType).append(" ").append(methodName).append("(");

        // Add parameters
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                newMethod.append(", ");
            }
            IVariableBinding param = parameters.get(i);
            newMethod.append(param.getType().getName()).append(" ").append(param.getName());
        }
        newMethod.append(") {\n");

        // Add selected statements
        for (Statement stmt : selectedStatements) {
            String stmtText = source.substring(stmt.getStartPosition(),
                    stmt.getStartPosition() + stmt.getLength());
            newMethod.append("\t\t").append(stmtText.trim()).append("\n");
        }

        // Add return statement if needed
        if (returnVar != null) {
            newMethod.append("\t\treturn ").append(returnVar.getName()).append(";\n");
        }
        newMethod.append("\t}\n");

        // Build the method call
        StringBuilder methodCall = new StringBuilder();
        if (returnVar != null) {
            methodCall.append(returnVar.getType().getName()).append(" ")
                    .append(returnVar.getName()).append(" = ");
        }
        methodCall.append(methodName).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                methodCall.append(", ");
            }
            methodCall.append(parameters.get(i).getName());
        }
        methodCall.append(");");

        // Build new source: replace selection with method call and add method at end of class
        int selectionStart = selectedStatements.get(0).getStartPosition();
        int selectionEnd = lastSelected.getStartPosition() + lastSelected.getLength();

        // Find indentation of first selected statement
        int lineStart = source.lastIndexOf('\n', selectionStart - 1) + 1;
        String indent = "";
        int idx = lineStart;
        while (idx < source.length() && (source.charAt(idx) == ' ' || source.charAt(idx) == '\t')) {
            idx++;
        }
        indent = source.substring(lineStart, idx);

        // Find the closing brace of the enclosing type to insert method before it
        int typeEnd = enclosingType.getStartPosition() + enclosingType.getLength();
        int closingBrace = source.lastIndexOf('}', typeEnd - 1);

        StringBuilder newSource = new StringBuilder();
        newSource.append(source, 0, selectionStart);
        newSource.append(indent).append(methodCall);
        newSource.append(source, selectionEnd, closingBrace);
        newSource.append(newMethod);
        newSource.append(source.substring(closingBrace));

        List<Map<String, Object>> edits = createWholeFileEdit(uri, source, newSource.toString());
        return createSuccessResult(edits);
    }

    private ASTNode findCoveringNode(CompilationUnit ast, int offset, int length) {
        NodeFinder finder = new NodeFinder(offset, length);
        ast.accept(finder);
        return finder.getCoveringNode();
    }

    private MethodDeclaration findEnclosingMethod(ASTNode node) {
        while (node != null) {
            if (node instanceof MethodDeclaration) {
                return (MethodDeclaration) node;
            }
            node = node.getParent();
        }
        return null;
    }

    private TypeDeclaration findEnclosingType(ASTNode node) {
        while (node != null) {
            if (node instanceof TypeDeclaration) {
                return (TypeDeclaration) node;
            }
            node = node.getParent();
        }
        return null;
    }

    private void collectDefinedVariables(ASTNode node, Set<IVariableBinding> vars) {
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment frag) {
                IVariableBinding binding = frag.resolveBinding();
                if (binding != null) {
                    vars.add(binding);
                }
                return true;
            }

            @Override
            public boolean visit(SingleVariableDeclaration decl) {
                IVariableBinding binding = decl.resolveBinding();
                if (binding != null) {
                    vars.add(binding);
                }
                return true;
            }
        });
    }

    private void collectUsedVariables(ASTNode node, Set<IVariableBinding> vars) {
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                IBinding binding = name.resolveBinding();
                if (binding instanceof IVariableBinding) {
                    vars.add((IVariableBinding) binding);
                }
                return true;
            }
        });
    }

    /**
     * Simple node finder that locates the deepest node covering the given range.
     */
    private static class NodeFinder extends ASTVisitor {
        private final int offset;
        private final int length;
        private ASTNode coveringNode;

        NodeFinder(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public boolean preVisit2(ASTNode node) {
            int nodeStart = node.getStartPosition();
            int nodeEnd = nodeStart + node.getLength();
            if (nodeStart <= offset && nodeEnd >= offset + length) {
                coveringNode = node;
                return true;
            }
            return false;
        }

        ASTNode getCoveringNode() {
            return coveringNode;
        }
    }
}
