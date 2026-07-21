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
package com.ibm.mcp.jdtls;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import com.ibm.mcp.jdtls.handlers.CallHierarchyHandler;
import com.ibm.mcp.jdtls.handlers.DiagnosticsHandler;
import com.ibm.mcp.jdtls.handlers.FindAnnotationUsagesHandler;
import com.ibm.mcp.jdtls.handlers.FindTypeInstantiationsHandler;
import com.ibm.mcp.jdtls.handlers.GetComplexityMetricsHandler;
import com.ibm.mcp.jdtls.handlers.TypeHierarchyHandler;

/**
 * Main delegate command handler for MCP JDT.LS extensions.
 * Dispatches incoming commands to specialized handlers.
 */
public class McpDelegateCommandHandler implements IDelegateCommandHandler {

    private static final Map<String, ICommandHandler> HANDLERS = new HashMap<>();

    static {
        HANDLERS.put("mcp.jdtls.diagnostics", new DiagnosticsHandler());
        HANDLERS.put("mcp.jdtls.typeHierarchy", new TypeHierarchyHandler());
        HANDLERS.put("mcp.jdtls.callHierarchyIncoming", new CallHierarchyHandler(true));
        HANDLERS.put("mcp.jdtls.callHierarchyOutgoing", new CallHierarchyHandler(false));
        HANDLERS.put("mcp.jdtls.findAnnotationUsages", new FindAnnotationUsagesHandler());
        HANDLERS.put("mcp.jdtls.findTypeInstantiations", new FindTypeInstantiationsHandler());
        HANDLERS.put("mcp.jdtls.getComplexityMetrics", new GetComplexityMetricsHandler());
    }

    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        ICommandHandler handler = HANDLERS.get(commandId);
        if (handler == null) {
            throw new UnsupportedOperationException("Unknown command: " + commandId);
        }
        return handler.execute(arguments, monitor);
    }
}
