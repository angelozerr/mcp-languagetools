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
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
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
 * Handler for "mcp.jdtls.introduceParameterObject" command.
 *
 * <p>Arguments: [{uri, line, character, className, parameterNames}]</p>
 *
 * <p>Introduces a parameter object by:
 * <ol>
 *   <li>Creating a new class with fields for the selected parameters</li>
 *   <li>Adding a constructor and getter methods</li>
 *   <li>Modifying the method signature to accept the new class instead of individual parameters</li>
 *   <li>Updating all call sites to create an instance of the new class</li>
 *   <li>Updating all references to the parameters in the method body to use getter calls</li>
 * </ol>
 * </p>
 *
 * <p>The new parameter object class is created in the same package as the original class.</p>
 */
public class IntroduceParameterObjectHandler extends AbstractRefactoringHandler {

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String className = (String) params.get("className");
        List<String> parameterNames = (List<String>) params.get("parameterNames");

        if (uri == null || className == null || className.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and className");
        }

        if (parameterNames == null || parameterNames.isEmpty()) {
            return createErrorResult("At least one parameter name must be specified");
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

        // Get method declaration
        ICompilationUnit declCu = method.getCompilationUnit();
        if (declCu == null) {
            return createErrorResult("Cannot find compilation unit for method declaration");
        }

        CompilationUnit declAst = parseAST(declCu, monitor);
        String declSource = declCu.getSource();

        MethodDeclaration methodDecl = findMethodDeclaration(declAst, method);
        if (methodDecl == null) {
            return createErrorResult("Cannot find method declaration in AST");
        }

        // Collect selected parameters and their types
        List<SingleVariableDeclaration> allParams = methodDecl.parameters();
        List<SingleVariableDeclaration> selectedParams = new ArrayList<>();
        List<SingleVariableDeclaration> remainingParams = new ArrayList<>();

        for (SingleVariableDeclaration param : allParams) {
            if (parameterNames.contains(param.getName().getIdentifier())) {
                selectedParams.add(param);
            } else {
                remainingParams.add(param);
            }
        }

        if (selectedParams.isEmpty()) {
            return createErrorResult("No matching parameters found in method signature");
        }

        // Build parameter info: name -> type
        List<ParamInfo> paramInfos = new ArrayList<>();
        for (SingleVariableDeclaration param : selectedParams) {
            String paramType = declSource.substring(param.getType().getStartPosition(),
                    param.getType().getStartPosition() + param.getType().getLength());
            paramInfos.add(new ParamInfo(param.getName().getIdentifier(), paramType));
        }

        // Build the parameter object class
        IType declaringType = method.getDeclaringType();
        IPackageFragment pkg = declaringType.getPackageFragment();
        String packageName = pkg.getElementName();

        StringBuilder classSource = new StringBuilder();
        if (!packageName.isEmpty()) {
            classSource.append("package ").append(packageName).append(";\n\n");
        }
        classSource.append("public class ").append(className).append(" {\n\n");

        // Fields
        for (ParamInfo pi : paramInfos) {
            classSource.append("\tprivate final ").append(pi.type).append(" ").append(pi.name).append(";\n");
        }
        classSource.append("\n");

        // Constructor
        classSource.append("\tpublic ").append(className).append("(");
        for (int i = 0; i < paramInfos.size(); i++) {
            if (i > 0) {
                classSource.append(", ");
            }
            ParamInfo pi = paramInfos.get(i);
            classSource.append(pi.type).append(" ").append(pi.name);
        }
        classSource.append(") {\n");
        for (ParamInfo pi : paramInfos) {
            classSource.append("\t\tthis.").append(pi.name).append(" = ").append(pi.name).append(";\n");
        }
        classSource.append("\t}\n\n");

        // Getters
        for (ParamInfo pi : paramInfos) {
            String capitalizedName = pi.name.substring(0, 1).toUpperCase() + pi.name.substring(1);
            String getterPrefix = "boolean".equals(pi.type) ? "is" : "get";
            classSource.append("\tpublic ").append(pi.type).append(" ").append(getterPrefix)
                    .append(capitalizedName).append("() {\n");
            classSource.append("\t\treturn ").append(pi.name).append(";\n");
            classSource.append("\t}\n\n");
        }
        classSource.append("}\n");

        // Modify the method signature
        String paramObjParamName = className.substring(0, 1).toLowerCase() + className.substring(1);

        // Build new parameter list
        StringBuilder newParamList = new StringBuilder();
        // Add remaining (non-selected) parameters first
        for (int i = 0; i < remainingParams.size(); i++) {
            if (newParamList.length() > 0) {
                newParamList.append(", ");
            }
            SingleVariableDeclaration param = remainingParams.get(i);
            newParamList.append(declSource.substring(param.getStartPosition(),
                    param.getStartPosition() + param.getLength()));
        }
        // Add the parameter object
        if (newParamList.length() > 0) {
            newParamList.append(", ");
        }
        newParamList.append(className).append(" ").append(paramObjParamName);

        // Find the parameter list range in the source
        int firstParamStart = allParams.get(0).getStartPosition();
        SingleVariableDeclaration lastParam = allParams.get(allParams.size() - 1);
        int lastParamEnd = lastParam.getStartPosition() + lastParam.getLength();

        StringBuilder newDeclSource = new StringBuilder(declSource);
        newDeclSource.replace(firstParamStart, lastParamEnd, newParamList.toString());

        // Replace parameter references in the method body with getter calls
        // This needs to be done after the parameter list replacement
        // For simplicity, do a string-based replacement in the method body
        for (ParamInfo pi : paramInfos) {
            String capitalizedName = pi.name.substring(0, 1).toUpperCase() + pi.name.substring(1);
            String getterPrefix = "boolean".equals(pi.type) ? "is" : "get";
            String getterCall = paramObjParamName + "." + getterPrefix + capitalizedName + "()";

            // Replace occurrences of the parameter name in the method body
            // This is a simplified approach - a proper implementation would use AST
            String bodyStr = newDeclSource.toString();
            // Use word boundary replacement to avoid partial matches
            bodyStr = bodyStr.replaceAll("\\b" + pi.name + "\\b", getterCall);
            // But restore the parameter declaration we just built
            bodyStr = bodyStr.replace(className + " " + getterCall, className + " " + paramObjParamName);
            newDeclSource = new StringBuilder(bodyStr);
        }

        List<Map<String, Object>> allEdits = new ArrayList<>();

        String declUri = declCu.getResource().getLocationURI().toString();
        if (!newDeclSource.toString().equals(declSource)) {
            allEdits.addAll(createWholeFileEdit(declUri, declSource, newDeclSource.toString()));
        }

        // Create the parameter object class file
        String classUri = declUri.substring(0, declUri.lastIndexOf('/') + 1) + className + ".java";
        allEdits.add(Map.of(
                "uri", classUri,
                "range", createRange(0, 0, 0, 0),
                "newText", classSource.toString()));

        // Update call sites
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
                                if (matchCu != null) {
                                    callSites.computeIfAbsent(matchCu, k -> new ArrayList<>())
                                            .add(new int[] { match.getOffset(), match.getLength() });
                                }
                            }
                        }
                    },
                    monitor);

            for (Map.Entry<ICompilationUnit, List<int[]>> entry : callSites.entrySet()) {
                ICompilationUnit callCu = entry.getKey();
                String callSource = callCu.getSource();
                String callUri = callCu.getResource().getLocationURI().toString();

                CompilationUnit callAst = parseAST(callCu, monitor);
                List<int[]> positions = entry.getValue();

                // For each call site, find the MethodInvocation and rewrite arguments
                positions.sort((a, b) -> b[0] - a[0]);
                StringBuilder newCallSource = new StringBuilder(callSource);

                for (int[] pos : positions) {
                    MethodInvocation invocation = findMethodInvocation(callAst, pos[0]);
                    if (invocation == null) {
                        continue;
                    }

                    List<Expression> args = invocation.arguments();
                    if (args.size() != allParams.size()) {
                        continue; // Skip if argument count doesn't match
                    }

                    // Build new argument list
                    StringBuilder newArgs = new StringBuilder();
                    // Add remaining (non-selected) arguments
                    for (int i = 0; i < allParams.size(); i++) {
                        String paramName = allParams.get(i).getName().getIdentifier();
                        if (!parameterNames.contains(paramName)) {
                            if (newArgs.length() > 0) {
                                newArgs.append(", ");
                            }
                            Expression arg = args.get(i);
                            newArgs.append(callSource.substring(arg.getStartPosition(),
                                    arg.getStartPosition() + arg.getLength()));
                        }
                    }

                    // Build the parameter object constructor call
                    if (newArgs.length() > 0) {
                        newArgs.append(", ");
                    }
                    newArgs.append("new ").append(className).append("(");
                    boolean first = true;
                    for (int i = 0; i < allParams.size(); i++) {
                        String paramName = allParams.get(i).getName().getIdentifier();
                        if (parameterNames.contains(paramName)) {
                            if (!first) {
                                newArgs.append(", ");
                            }
                            Expression arg = args.get(i);
                            newArgs.append(callSource.substring(arg.getStartPosition(),
                                    arg.getStartPosition() + arg.getLength()));
                            first = false;
                        }
                    }
                    newArgs.append(")");

                    // Replace the argument list
                    int argsStart = args.get(0).getStartPosition();
                    Expression lastArg = args.get(args.size() - 1);
                    int argsEnd = lastArg.getStartPosition() + lastArg.getLength();

                    newCallSource.replace(argsStart, argsEnd, newArgs.toString());
                }

                String result = newCallSource.toString();
                if (!result.equals(callSource)) {
                    allEdits.addAll(createWholeFileEdit(callUri, callSource, result));
                }
            }
        }

        return createSuccessResult(allEdits);
    }

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

    private static class ParamInfo {
        final String name;
        final String type;

        ParamInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}
