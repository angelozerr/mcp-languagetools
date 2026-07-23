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
import com.ibm.mcp.jdtls.handlers.analysis.*;
import com.ibm.mcp.jdtls.handlers.codegen.*;
import com.ibm.mcp.jdtls.handlers.diagnostics.*;
import com.ibm.mcp.jdtls.handlers.framework.*;
import com.ibm.mcp.jdtls.handlers.navigation.*;
import com.ibm.mcp.jdtls.handlers.project.*;
import com.ibm.mcp.jdtls.handlers.quality.*;
import com.ibm.mcp.jdtls.handlers.refactoring.*;
import com.ibm.mcp.jdtls.handlers.search.*;

/**
 * Main delegate command handler for MCP JDT.LS extensions.
 * Dispatches incoming commands to specialized handlers.
 */
public class McpDelegateCommandHandler implements IDelegateCommandHandler {

    private static final Map<String, ICommandHandler> HANDLERS = new HashMap<>();

    static {
        // --- Existing handlers ---
        HANDLERS.put("mcp.jdtls.diagnostics", new DiagnosticsHandler());
        HANDLERS.put("mcp.jdtls.typeHierarchy", new TypeHierarchyHandler());
        HANDLERS.put("mcp.jdtls.callHierarchyIncoming", new CallHierarchyHandler(true));
        HANDLERS.put("mcp.jdtls.callHierarchyOutgoing", new CallHierarchyHandler(false));
        HANDLERS.put("mcp.jdtls.findAnnotationUsages", new FindAnnotationUsagesHandler());
        HANDLERS.put("mcp.jdtls.findTypeInstantiations", new FindTypeInstantiationsHandler());
        HANDLERS.put("mcp.jdtls.getComplexityMetrics", new GetComplexityMetricsHandler());

        // --- Fine-grained reference search ---
        HANDLERS.put("mcp.jdtls.findCasts", new FindCastsHandler());
        HANDLERS.put("mcp.jdtls.findCatchBlocks", new FindCatchBlocksHandler());
        HANDLERS.put("mcp.jdtls.findInstanceofChecks", new FindInstanceofChecksHandler());
        HANDLERS.put("mcp.jdtls.findThrowsDeclarations", new FindThrowsDeclarationsHandler());
        HANDLERS.put("mcp.jdtls.findTypeArguments", new FindTypeArgumentsHandler());
        HANDLERS.put("mcp.jdtls.findMethodReferences", new FindMethodReferencesHandler());

        // --- Code search ---
        HANDLERS.put("mcp.jdtls.findFieldWrites", new FindFieldWritesHandler());
        HANDLERS.put("mcp.jdtls.findTests", new FindTestsHandler());
        HANDLERS.put("mcp.jdtls.findAffectedTests", new FindAffectedTestsHandler());
        HANDLERS.put("mcp.jdtls.findUnusedCode", new FindUnusedCodeHandler());
        HANDLERS.put("mcp.jdtls.findUnreachableCode", new FindUnreachableCodeHandler());
        HANDLERS.put("mcp.jdtls.findReflectionUsage", new FindReflectionUsageHandler());
        HANDLERS.put("mcp.jdtls.suggestImports", new SuggestImportsHandler());
        HANDLERS.put("mcp.jdtls.getTypeUsageSummary", new GetTypeUsageSummaryHandler());
        HANDLERS.put("mcp.jdtls.searchSymbols", new SearchSymbolsHandler());
        HANDLERS.put("mcp.jdtls.findReferences", new FindReferencesHandler());
        HANDLERS.put("mcp.jdtls.findImplementations", new FindImplementationsHandler());

        // --- Navigation ---
        HANDLERS.put("mcp.jdtls.goToDefinition", new GoToDefinitionHandler());
        HANDLERS.put("mcp.jdtls.getHoverInfo", new GetHoverInfoHandler());
        HANDLERS.put("mcp.jdtls.getJavadoc", new GetJavadocHandler());
        HANDLERS.put("mcp.jdtls.getSymbolInfo", new GetSymbolInfoHandler());
        HANDLERS.put("mcp.jdtls.getEnclosingElement", new GetEnclosingElementHandler());
        HANDLERS.put("mcp.jdtls.getFieldAtPosition", new GetFieldAtPositionHandler());
        HANDLERS.put("mcp.jdtls.getMethodAtPosition", new GetMethodAtPositionHandler());
        HANDLERS.put("mcp.jdtls.getTypeAtPosition", new GetTypeAtPositionHandler());
        HANDLERS.put("mcp.jdtls.getSignatureHelp", new GetSignatureHelpHandler());
        HANDLERS.put("mcp.jdtls.getSuperMethod", new GetSuperMethodHandler());
        HANDLERS.put("mcp.jdtls.getDocumentSymbols", new GetDocumentSymbolsHandler());
        HANDLERS.put("mcp.jdtls.getTypeMembers", new GetTypeMembersHandler());

        // --- Analysis ---
        HANDLERS.put("mcp.jdtls.getDependencyGraph", new GetDependencyGraphHandler());
        HANDLERS.put("mcp.jdtls.analyzeChangeImpact", new AnalyzeChangeImpactHandler());
        HANDLERS.put("mcp.jdtls.analyzeControlFlow", new AnalyzeControlFlowHandler());
        HANDLERS.put("mcp.jdtls.analyzeDataFlow", new AnalyzeDataFlowHandler());
        HANDLERS.put("mcp.jdtls.analyzeFile", new AnalyzeFileHandler());
        HANDLERS.put("mcp.jdtls.analyzeMethod", new AnalyzeMethodHandler());
        HANDLERS.put("mcp.jdtls.analyzeType", new AnalyzeTypeHandler());

        // --- Code quality ---
        HANDLERS.put("mcp.jdtls.findLargeClasses", new FindLargeClassesHandler());
        HANDLERS.put("mcp.jdtls.findNamingViolations", new FindNamingViolationsHandler());
        HANDLERS.put("mcp.jdtls.findPossibleBugs", new FindPossibleBugsHandler());
        HANDLERS.put("mcp.jdtls.findCircularDependencies", new FindCircularDependenciesHandler());

        // --- Diagnostics & quick fixes ---
        HANDLERS.put("mcp.jdtls.validateSyntax", new ValidateSyntaxHandler());
        HANDLERS.put("mcp.jdtls.getQuickFixes", new GetQuickFixesHandler());
        HANDLERS.put("mcp.jdtls.applyQuickFix", new ApplyQuickFixHandler());
        HANDLERS.put("mcp.jdtls.diagnoseAndFix", new DiagnoseAndFixHandler());
        HANDLERS.put("mcp.jdtls.applyCleanup", new ApplyCleanupHandler());

        // --- Framework-specific ---
        HANDLERS.put("mcp.jdtls.getHttpEndpoints", new GetHttpEndpointsHandler());
        HANDLERS.put("mcp.jdtls.getJpaModel", new GetJpaModelHandler());
        HANDLERS.put("mcp.jdtls.getDiRegistrations", new GetDiRegistrationsHandler());

        // --- Refactoring ---
        HANDLERS.put("mcp.jdtls.renameSymbol", new RenameSymbolHandler());
        HANDLERS.put("mcp.jdtls.organizeImports", new OrganizeImportsHandler());
        HANDLERS.put("mcp.jdtls.extractMethod", new ExtractMethodHandler());
        HANDLERS.put("mcp.jdtls.extractVariable", new ExtractVariableHandler());
        HANDLERS.put("mcp.jdtls.extractConstant", new ExtractConstantHandler());
        HANDLERS.put("mcp.jdtls.extractInterface", new ExtractInterfaceHandler());
        HANDLERS.put("mcp.jdtls.extractSuperclass", new ExtractSuperclassHandler());
        HANDLERS.put("mcp.jdtls.inlineMethod", new InlineMethodHandler());
        HANDLERS.put("mcp.jdtls.inlineVariable", new InlineVariableHandler());
        HANDLERS.put("mcp.jdtls.changeMethodSignature", new ChangeMethodSignatureHandler());
        HANDLERS.put("mcp.jdtls.convertAnonymousToLambda", new ConvertAnonymousToLambdaHandler());
        HANDLERS.put("mcp.jdtls.encapsulateField", new EncapsulateFieldHandler());
        HANDLERS.put("mcp.jdtls.introduceParameterObject", new IntroduceParameterObjectHandler());
        HANDLERS.put("mcp.jdtls.moveTypeToNewFile", new MoveTypeToNewFileHandler());
        HANDLERS.put("mcp.jdtls.pullUp", new PullUpHandler());
        HANDLERS.put("mcp.jdtls.pushDown", new PushDownHandler());

        // --- Code generation ---
        HANDLERS.put("mcp.jdtls.generateGettersSetters", new GenerateGettersSettersHandler());
        HANDLERS.put("mcp.jdtls.generateConstructor", new GenerateConstructorHandler());
        HANDLERS.put("mcp.jdtls.generateToString", new GenerateToStringHandler());
        HANDLERS.put("mcp.jdtls.generateEqualsHashCode", new GenerateEqualsHashCodeHandler());

        // --- Project ---
        HANDLERS.put("mcp.jdtls.getProjectStructure", new GetProjectStructureHandler());
        HANDLERS.put("mcp.jdtls.getClasspathInfo", new GetClasspathInfoHandler());
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
