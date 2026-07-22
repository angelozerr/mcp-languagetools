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
package com.ibm.mcp.jdtls.handlers.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;

import com.ibm.mcp.jdtls.ICommandHandler;
import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Handler for "mcp.jdtls.findImplementations" command.
 *
 * <p>Arguments: [{fullyQualifiedName}] or [{uri, line, character}]</p>
 *
 * <p>Resolves the type and uses {@link ITypeHierarchy#getAllSubtypes} to find
 * all implementations (for interfaces) or subclasses (for classes).</p>
 */
public class FindImplementationsHandler implements ICommandHandler {

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        IType type = JdtUtils.resolveType(arguments, monitor);
        if (type == null || !type.exists()) {
            return Map.of("error", "Type not found");
        }

        ITypeHierarchy hierarchy = type.newTypeHierarchy(monitor);
        IType[] subtypes = hierarchy.getAllSubtypes(type);

        List<Map<String, Object>> implementations = new ArrayList<>();
        for (IType subtype : subtypes) {
            if (monitor.isCanceled()) {
                break;
            }
            Map<String, Object> impl = new HashMap<>();
            impl.put("name", subtype.getElementName());
            impl.put("fqn", subtype.getFullyQualifiedName());
            impl.put("isInterface", Flags.isInterface(subtype.getFlags()));
            if (subtype.getResource() != null) {
                impl.put("uri", subtype.getResource().getLocationURI().toString());
            }
            implementations.add(impl);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", type.getFullyQualifiedName());
        result.put("count", implementations.size());
        result.put("implementations", implementations);
        return result;
    }
}
