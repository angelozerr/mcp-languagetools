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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP tools for Java refactoring via JDT.LS delegate command handlers.
 *
 * <p>Tools adapted from <a href="https://github.com/pzalutski-pixel/javalens-mcp">javalens-mcp</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
@ApplicationScoped
public class JavaRefactoringTools {

    @Inject
    JdtlsCommandExecutor executor;

    @Tool(name = "java_rename_symbol",
          description = "Rename a Java symbol across the entire project. " +
                        "Returns text edits for all affected files. " +
                        "Example: java_rename_symbol(cwd='/project', fileUri='file:///project/src/Main.java', line=10, character=5, newName='newMethodName')")
    public CompletableFuture<String> renameSymbol(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "New name for the symbol") String newName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.renameSymbol",
                Map.of("uri", fileUri, "line", line, "character", character, "newName", newName),
                cancellation, progress);
    }

    @Tool(name = "java_organize_imports",
          description = "Organize imports in a Java file: remove unused imports and sort. " +
                        "Example: java_organize_imports(cwd='/project', fileUri='file:///project/src/Main.java')")
    public CompletableFuture<String> organizeImports(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.organizeImports",
                Map.of("uri", fileUri),
                cancellation, progress);
    }

    @Tool(name = "java_extract_method",
          description = "Extract a code selection into a new method. " +
                        "Analyzes variables to determine parameters and return type. " +
                        "Example: java_extract_method(cwd='/project', fileUri='file:///...', startLine=10, startCharacter=0, endLine=15, endCharacter=0, methodName='extractedMethod')")
    public CompletableFuture<String> extractMethod(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = "Start line of the selection (0-based)") int startLine,
            @ToolArg(description = "Start character of the selection (0-based)") int startCharacter,
            @ToolArg(description = "End line of the selection (0-based)") int endLine,
            @ToolArg(description = "End character of the selection (0-based)") int endCharacter,
            @ToolArg(description = "Name for the extracted method") String methodName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.extractMethod",
                Map.of("uri", fileUri, "startLine", startLine, "startCharacter", startCharacter,
                       "endLine", endLine, "endCharacter", endCharacter, "methodName", methodName),
                cancellation, progress);
    }

    @Tool(name = "java_extract_variable",
          description = "Extract an expression into a local variable. " +
                        "Example: java_extract_variable(cwd='/project', fileUri='file:///...', startLine=10, startCharacter=5, endLine=10, endCharacter=30, variableName='result')")
    public CompletableFuture<String> extractVariable(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = "Start line of the expression (0-based)") int startLine,
            @ToolArg(description = "Start character of the expression (0-based)") int startCharacter,
            @ToolArg(description = "End line of the expression (0-based)") int endLine,
            @ToolArg(description = "End character of the expression (0-based)") int endCharacter,
            @ToolArg(description = "Name for the new variable") String variableName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.extractVariable",
                Map.of("uri", fileUri, "startLine", startLine, "startCharacter", startCharacter,
                       "endLine", endLine, "endCharacter", endCharacter, "variableName", variableName),
                cancellation, progress);
    }

    @Tool(name = "java_extract_constant",
          description = "Extract an expression into a static final constant field. " +
                        "Example: java_extract_constant(cwd='/project', fileUri='file:///...', startLine=10, startCharacter=5, endLine=10, endCharacter=30, constantName='MAX_SIZE')")
    public CompletableFuture<String> extractConstant(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = "Start line of the expression (0-based)") int startLine,
            @ToolArg(description = "Start character of the expression (0-based)") int startCharacter,
            @ToolArg(description = "End line of the expression (0-based)") int endLine,
            @ToolArg(description = "End character of the expression (0-based)") int endCharacter,
            @ToolArg(description = "Name for the constant (e.g., 'MAX_SIZE')") String constantName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.extractConstant",
                Map.of("uri", fileUri, "startLine", startLine, "startCharacter", startCharacter,
                       "endLine", endLine, "endCharacter", endCharacter, "constantName", constantName),
                cancellation, progress);
    }

    @Tool(name = "java_extract_interface",
          description = "Extract an interface from a Java class with selected methods. " +
                        "Creates a new interface file and adds implements clause to the class. " +
                        "Example: java_extract_interface(cwd='/project', fileUri='file:///...', line=5, character=10, interfaceName='IService')")
    public CompletableFuture<String> extractInterface(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "Name for the new interface") String interfaceName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.extractInterface",
                Map.of("uri", fileUri, "line", line, "character", character, "interfaceName", interfaceName),
                cancellation, progress);
    }

    @Tool(name = "java_extract_superclass",
          description = "Extract a superclass from a Java class with selected members. " +
                        "Creates a new abstract superclass and modifies the original class to extend it. " +
                        "Example: java_extract_superclass(cwd='/project', fileUri='file:///...', line=5, character=10, superclassName='AbstractService')")
    public CompletableFuture<String> extractSuperclass(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "Name for the new superclass") String superclassName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.extractSuperclass",
                Map.of("uri", fileUri, "line", line, "character", character, "superclassName", superclassName),
                cancellation, progress);
    }

    @Tool(name = "java_inline_method",
          description = "Inline a method by replacing its call sites with the method body. " +
                        "Example: java_inline_method(cwd='/project', fileUri='file:///...', line=15, character=10)")
    public CompletableFuture<String> inlineMethod(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.inlineMethod",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_inline_variable",
          description = "Inline a local variable by replacing all usages with its initializer expression. " +
                        "Example: java_inline_variable(cwd='/project', fileUri='file:///...', line=15, character=10)")
    public CompletableFuture<String> inlineVariable(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.inlineVariable",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_change_method_signature",
          description = "Change a method's signature (name, return type, parameters) and update all call sites. " +
                        "Example: java_change_method_signature(cwd='/project', fileUri='file:///...', line=10, character=5, newName='updatedMethod')")
    public CompletableFuture<String> changeMethodSignature(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "New method name (optional, keep current if empty)") String newName,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.changeMethodSignature",
                Map.of("uri", fileUri, "line", line, "character", character, "newName", newName),
                cancellation, progress);
    }

    @Tool(name = "java_convert_anonymous_to_lambda",
          description = "Convert an anonymous class to a lambda expression (for functional interfaces). " +
                        "Example: java_convert_anonymous_to_lambda(cwd='/project', fileUri='file:///...', line=15, character=20)")
    public CompletableFuture<String> convertAnonymousToLambda(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.convertAnonymousToLambda",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_encapsulate_field",
          description = "Encapsulate a field by generating getter/setter methods and updating all direct accesses. " +
                        "Example: java_encapsulate_field(cwd='/project', fileUri='file:///...', line=8, character=10)")
    public CompletableFuture<String> encapsulateField(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.encapsulateField",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_introduce_parameter_object",
          description = "Bundle method parameters into a parameter object class. " +
                        "Creates a new class and modifies the method signature and all call sites. " +
                        "Example: java_introduce_parameter_object(cwd='/project', fileUri='file:///...', line=10, character=5, className='SearchCriteria')")
    public CompletableFuture<String> introduceParameterObject(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            @ToolArg(description = "Name for the new parameter object class") String className,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.introduceParameterObject",
                Map.of("uri", fileUri, "line", line, "character", character, "className", className),
                cancellation, progress);
    }

    @Tool(name = "java_move_type_to_new_file",
          description = "Move a nested/inner type to its own top-level file. " +
                        "Example: java_move_type_to_new_file(cwd='/project', fileUri='file:///...', line=20, character=10)")
    public CompletableFuture<String> moveTypeToNewFile(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.moveTypeToNewFile",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_pull_up",
          description = "Pull members up from a subclass into its superclass. " +
                        "Example: java_pull_up(cwd='/project', fileUri='file:///...', line=5, character=10)")
    public CompletableFuture<String> pullUp(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.pullUp",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }

    @Tool(name = "java_push_down",
          description = "Push members down from a superclass into its subclasses. " +
                        "Example: java_push_down(cwd='/project', fileUri='file:///...', line=5, character=10)")
    public CompletableFuture<String> pushDown(
            @ToolArg(description = ToolArgDescriptions.CWD) String cwd,
            @ToolArg(description = ToolArgDescriptions.FILE_URI) String fileUri,
            @ToolArg(description = ToolArgDescriptions.POSITION_LINE) int line,
            @ToolArg(description = ToolArgDescriptions.POSITION_CHARACTER) int character,
            Cancellation cancellation,
            Progress progress) {
        return executor.executeCommand(cwd, "mcp.jdtls.pushDown",
                Map.of("uri", fileUri, "line", line, "character", character),
                cancellation, progress);
    }
}
