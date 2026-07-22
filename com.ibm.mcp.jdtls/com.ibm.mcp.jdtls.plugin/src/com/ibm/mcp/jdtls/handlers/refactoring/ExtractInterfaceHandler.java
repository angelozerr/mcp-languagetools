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
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.extractInterface" command.
 *
 * <p>Arguments: [{uri, line, character, interfaceName, methodNames}]</p>
 *
 * <p>Extracts an interface from a class by:
 * <ol>
 *   <li>Creating a new interface file with abstract method signatures from the selected methods</li>
 *   <li>Adding {@code implements InterfaceName} to the original class declaration</li>
 * </ol>
 * </p>
 *
 * <p>The new interface file is created in the same package as the original class.
 * Method signatures are copied without bodies. Parameter types are resolved using
 * JDT's Signature utilities.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/ExtractInterfaceTool.java">javalens-mcp ExtractInterfaceTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public class ExtractInterfaceHandler extends AbstractRefactoringHandler {

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        Map<String, Object> params = parseParams(arguments);
        String uri = (String) params.get("uri");
        String interfaceName = (String) params.get("interfaceName");
        List<String> methodNames = (List<String>) params.get("methodNames");

        if (uri == null || interfaceName == null || interfaceName.isEmpty()) {
            return createErrorResult("Missing required arguments: uri and interfaceName");
        }

        if (methodNames == null || methodNames.isEmpty()) {
            return createErrorResult("At least one method name must be specified");
        }

        int line = ((Number) params.get("line")).intValue();
        int character = ((Number) params.get("character")).intValue();

        IType type = JdtUtils.resolveTypeAtPosition(uri, line, character, monitor);
        if (type == null) {
            return createErrorResult("No type found at position");
        }

        if (type.isInterface()) {
            return createErrorResult("Cannot extract interface from an interface");
        }

        ICompilationUnit cu = type.getCompilationUnit();
        if (cu == null) {
            return createErrorResult("Compilation unit not found for type");
        }

        String source = cu.getSource();
        IPackageFragment pkg = type.getPackageFragment();
        String packageName = pkg.getElementName();

        // Build the interface source
        StringBuilder interfaceSource = new StringBuilder();
        if (!packageName.isEmpty()) {
            interfaceSource.append("package ").append(packageName).append(";\n\n");
        }
        interfaceSource.append("public interface ").append(interfaceName).append(" {\n\n");

        // Add method signatures
        IMethod[] methods = type.getMethods();
        for (IMethod method : methods) {
            if (methodNames.contains(method.getElementName())) {
                interfaceSource.append("\t");
                // Build return type
                String returnType = Signature.toString(method.getReturnType());
                interfaceSource.append(returnType).append(" ");
                interfaceSource.append(method.getElementName()).append("(");

                // Build parameters
                String[] paramTypes = method.getParameterTypes();
                String[] paramNames = method.getParameterNames();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0) {
                        interfaceSource.append(", ");
                    }
                    interfaceSource.append(Signature.toString(paramTypes[i]));
                    interfaceSource.append(" ").append(paramNames[i]);
                }
                interfaceSource.append(")");

                // Add throws clause
                String[] exceptionTypes = method.getExceptionTypes();
                if (exceptionTypes.length > 0) {
                    interfaceSource.append(" throws ");
                    for (int i = 0; i < exceptionTypes.length; i++) {
                        if (i > 0) {
                            interfaceSource.append(", ");
                        }
                        interfaceSource.append(Signature.toString(exceptionTypes[i]));
                    }
                }

                interfaceSource.append(";\n\n");
            }
        }
        interfaceSource.append("}\n");

        // Modify the original class to add "implements InterfaceName"
        CompilationUnit ast = parseAST(cu, monitor);
        String typeName = type.getElementName();

        // Find the type declaration in source
        String classKeyword = "class " + typeName;
        int classIdx = source.indexOf(classKeyword);
        if (classIdx < 0) {
            return createErrorResult("Cannot find class declaration in source");
        }

        // Find where to insert "implements" clause
        int braceIdx = source.indexOf('{', classIdx);
        if (braceIdx < 0) {
            return createErrorResult("Cannot find class body opening brace");
        }

        // Check if "implements" already exists
        String beforeBrace = source.substring(classIdx, braceIdx).trim();
        String newSource;
        if (beforeBrace.contains("implements")) {
            // Add to existing implements clause
            int implementsIdx = source.indexOf("implements", classIdx);
            int afterImplements = implementsIdx + "implements".length();
            newSource = source.substring(0, afterImplements) + " " + interfaceName + ","
                    + source.substring(afterImplements);
        } else {
            // Add new implements clause before the opening brace
            newSource = source.substring(0, braceIdx) + "implements " + interfaceName + " "
                    + source.substring(braceIdx);
        }

        // Build the interface file URI
        String originalUri = cu.getResource().getLocationURI().toString();
        String interfaceUri = originalUri.substring(0, originalUri.lastIndexOf('/') + 1)
                + interfaceName + ".java";

        // Create edits for both files
        List<Map<String, Object>> edits = new ArrayList<>();

        // Edit 1: Modify original class
        edits.addAll(createWholeFileEdit(uri, source, newSource));

        // Edit 2: Create the new interface file (insert at position 0,0 of new file)
        edits.add(Map.of(
                "uri", interfaceUri,
                "range", createRange(0, 0, 0, 0),
                "newText", interfaceSource.toString()));

        return createSuccessResult(edits);
    }
}
