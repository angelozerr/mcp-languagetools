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
package com.ibm.mcp.languagetools.extensions.jdtls.tools;

import com.ibm.mcp.languagetools.tools.ToolArgDescriptions;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for Java framework analysis via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaFrameworkTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_get_http_endpoints",
          description = "Find all HTTP endpoints (REST API routes) in a Java project. " +
                        "Detects Spring MVC (@GetMapping, @PostMapping, @RequestMapping) and " +
                        "JAX-RS (@GET, @POST, @Path) annotations. " +
                        "Example: java_get_http_endpoints(cwd='/project')")
    public CompletableFuture<String> getHttpEndpoints(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_HTTP_ENDPOINTS,
                Map.of(),
                cancellation, progress);
    }

    @Tool(name = "java_get_jpa_model",
          description = "Get the JPA entity model from a Java project. " +
                        "Returns entities, fields, column mappings, and relationships " +
                        "(@Entity, @Table, @Column, @ManyToOne, @OneToMany, etc.). " +
                        "Example: java_get_jpa_model(cwd='/project')")
    public CompletableFuture<String> getJpaModel(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, JdtlsCommands.GET_JPA_MODEL,
                Map.of(),
                cancellation, progress);
    }

    @Tool(name = "java_get_di_registrations",
          description = "Find dependency injection registrations in a Java project. " +
                        "Detects Spring (@Component, @Service, @Repository, @Controller, @Bean, @Autowired) and " +
                        "Jakarta CDI (@ApplicationScoped, @Inject, @Produces, @Named) annotations. " +
                        "Use scope='project' for faster project-only scan (default scans entire workspace). " +
                        "Example: java_get_di_registrations(cwd='/project', scope='project')")
    public CompletableFuture<String> getDiRegistrations(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = JavaToolArgDescriptions.SEARCH_SCOPE, required = false) String scope,
            @ToolArg(description = JavaToolArgDescriptions.PROJECT_NAME, required = false) String projectName,
            Cancellation cancellation,
            Progress progress) {
        Map<String, Object> args = new HashMap<>();
        RefactoringHelper.putScope(args, scope, projectName);
        return executor.executeCommand(cwd, JdtlsCommands.GET_DI_REGISTRATIONS,
                args,
                cancellation, progress);
    }
}
