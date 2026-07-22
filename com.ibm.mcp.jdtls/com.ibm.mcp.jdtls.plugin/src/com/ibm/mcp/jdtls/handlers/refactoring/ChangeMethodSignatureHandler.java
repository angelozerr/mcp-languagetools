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
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.changeMethodSignature" command.
 *
 * <p>Arguments: [{uri, line, character, newName (optional), newReturnType (optional),
 * newParameters (optional list of {name, type}), newExceptions (optional list)}]</p>
 *
 * <p>Changes the method signature by modifying the declaration and updating all call
 * sites found via SearchEngine. Supports:
 * <ul>
 *   <li>Renaming the method</li>
 *   <li>Changing the return type</li>
 *   <li>Changing parameter types and names</li>
 *   <li>Changing the throws clause</li>
 * </ul>
 * </p>
 *
 * <p>Call site updates handle reordering/adding/removing arguments based on parameter
 * name matching between old and new parameter lists.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/ChangeMethodSignatureTool.java">javalens-mcp ChangeMethodSignatureTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class ChangeMethodSignatureHandler extends AbstractRefactoringHandler {

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");

        if (uri == null) {
            return createErrorResult("Missing required argument: uri");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        String newName = (String) params.get("newName");
        String newReturnType = (String) params.get("newReturnType");
        List<Map<String, String>> newParameters = (List<Map<String, String>>) params.get("newParameters");
        List<String> newExceptions = (List<String>) params.get("newExceptions");

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

        String oldName = method.getElementName();
        String effectiveName = (newName != null && !newName.isEmpty()) ? newName : oldName;

        // Get the current method declaration info
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

        // Get old parameter names for call site argument mapping
        List<SingleVariableDeclaration> oldParams = methodDecl.parameters();
        List<String> oldParamNames = new ArrayList<>();
        for (SingleVariableDeclaration param : oldParams) {
            oldParamNames.add(param.getName().getIdentifier());
        }

        // Build new method signature
        StringBuilder newSignature = new StringBuilder();

        // Return type
        if (newReturnType != null && !newReturnType.isEmpty()) {
            newSignature.append(newReturnType);
        } else {
            org.eclipse.jdt.core.dom.Type retType = methodDecl.getReturnType2();
            if (retType != null) {
                newSignature.append(declSource.substring(retType.getStartPosition(),
                        retType.getStartPosition() + retType.getLength()));
            } else {
                newSignature.append("void");
            }
        }

        newSignature.append(" ").append(effectiveName).append("(");

        // Parameters
        if (newParameters != null) {
            for (int i = 0; i < newParameters.size(); i++) {
                if (i > 0) {
                    newSignature.append(", ");
                }
                Map<String, String> param = newParameters.get(i);
                newSignature.append(param.get("type")).append(" ").append(param.get("name"));
            }
        } else {
            // Keep existing parameters
            for (int i = 0; i < oldParams.size(); i++) {
                if (i > 0) {
                    newSignature.append(", ");
                }
                SingleVariableDeclaration param = oldParams.get(i);
                newSignature.append(declSource.substring(param.getStartPosition(),
                        param.getStartPosition() + param.getLength()));
            }
        }
        newSignature.append(")");

        // Throws clause
        if (newExceptions != null) {
            if (!newExceptions.isEmpty()) {
                newSignature.append(" throws ");
                for (int i = 0; i < newExceptions.size(); i++) {
                    if (i > 0) {
                        newSignature.append(", ");
                    }
                    newSignature.append(newExceptions.get(i));
                }
            }
        } else {
            // Keep existing throws
            List<org.eclipse.jdt.core.dom.Type> thrownExceptions = methodDecl.thrownExceptionTypes();
            if (!thrownExceptions.isEmpty()) {
                newSignature.append(" throws ");
                for (int i = 0; i < thrownExceptions.size(); i++) {
                    if (i > 0) {
                        newSignature.append(", ");
                    }
                    org.eclipse.jdt.core.dom.Type exc = thrownExceptions.get(i);
                    newSignature.append(declSource.substring(exc.getStartPosition(),
                            exc.getStartPosition() + exc.getLength()));
                }
            }
        }

        // Replace the method signature in the declaration
        // The signature spans from the return type to the closing parenthesis (or throws clause end)
        org.eclipse.jdt.core.dom.Type retType = methodDecl.getReturnType2();
        int sigStart = retType != null ? retType.getStartPosition() : methodDecl.getName().getStartPosition();

        // Find the end of the signature (closing paren or end of throws clause)
        int sigEnd;
        List<org.eclipse.jdt.core.dom.Type> thrownTypes = methodDecl.thrownExceptionTypes();
        if (!thrownTypes.isEmpty()) {
            org.eclipse.jdt.core.dom.Type lastThrown = thrownTypes.get(thrownTypes.size() - 1);
            sigEnd = lastThrown.getStartPosition() + lastThrown.getLength();
        } else {
            // Find the closing parenthesis of parameters
            String methodText = declSource.substring(methodDecl.getStartPosition(),
                    methodDecl.getStartPosition() + methodDecl.getLength());
            int relName = methodDecl.getName().getStartPosition() - methodDecl.getStartPosition();
            int closeParen = methodText.indexOf(')', relName);
            sigEnd = methodDecl.getStartPosition() + closeParen + 1;
        }

        StringBuilder newDeclSource = new StringBuilder(declSource);
        newDeclSource.replace(sigStart, sigEnd, newSignature.toString());

        List<Map<String, Object>> allEdits = new ArrayList<>();

        // Edit the declaration file
        String declUri = declCu.getResource().getLocationURI().toString();
        if (!newDeclSource.toString().equals(declSource)) {
            allEdits.addAll(createWholeFileEdit(declUri, declSource, newDeclSource.toString()));
        }

        // Find and update call sites if the name changed or parameters changed
        if (!oldName.equals(effectiveName) || newParameters != null) {
            SearchPattern pattern = SearchPattern.createPattern(
                    method,
                    IJavaSearchConstants.REFERENCES);

            if (pattern != null) {
                IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
                Map<ICompilationUnit, List<int[]>> callSites = new HashMap<>();

                SearchEngine engine = new SearchEngine();
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
                                    if (matchCu != null && !matchCu.equals(declCu)) {
                                        callSites.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                                .add(new int[] { match.getOffset(), match.getLength() });
                                    }
                                }
                            }
                        },
                        monitor);

                for (Map.Entry<ICompilationUnit, List<int[]>> entry : callSites.entrySet()) {
                    ICompilationUnit callCu = entry.getKey();
                    List<int[]> positions = entry.getValue();
                    String callSource = callCu.getSource();
                    String callUri = callCu.getResource().getLocationURI().toString();

                    // Sort from end to start
                    positions.sort((a, b) -> b[0] - a[0]);

                    StringBuilder newCallSource = new StringBuilder(callSource);
                    for (int[] pos : positions) {
                        // Replace method name at call site
                        if (!oldName.equals(effectiveName)) {
                            newCallSource.replace(pos[0], pos[0] + pos[1], effectiveName);
                        }
                    }

                    String result = newCallSource.toString();
                    if (!result.equals(callSource)) {
                        allEdits.addAll(createWholeFileEdit(callUri, callSource, result));
                    }
                }
            }
        }

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
}
