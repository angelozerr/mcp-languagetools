# MCP Language Tools

> **Note**: This is a Proof of Concept (POC) project.

MCP Language Tools is an [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) server that gives AI assistants the power of [LSP (Language Server Protocol)](https://microsoft.github.io/language-server-protocol/) and [DAP (Debug Adapter Protocol)](https://microsoft.github.io/debug-adapter-protocol/) through MCP tools.

## What is it?

**MCP Language Tools acts as a platform for [LSP (Language Server Protocol)](https://microsoft.github.io/language-server-protocol/) language servers and [DAP (Debug Adapter Protocol)](https://microsoft.github.io/debug-adapter-protocol/) debug adapters**, similar to VS Code. Just as VS Code hosts extensions to provide IDE features, MCP Language Tools manages LSP and DAP servers and exposes their capabilities to any AI assistant through MCP.

This means AI assistants can leverage the same tooling that developers use in their IDEs: diagnostics, code navigation, refactoring, debugging, and more — for **any language**.

### Key highlights

- **Multi-language support**: Java, JavaScript, Python, Go, Rust, C/C++, XML, YAML, Kotlin, Dart, PHP, Lua, Dockerfile, and more
- **Both LSP and DAP**: Full language server support (diagnostics, navigation, refactoring) and debug adapter support (breakpoints, stepping, variables)
- **Server collaboration**: LSP and DAP servers can communicate with each other (e.g., MicroProfile LS leverages JDT.LS for Java type resolution). See [Bind Mechanism](docs/bind-mechanism.md)
- **Auto-installation**: Language servers and debug adapters are automatically downloaded and installed on first use
- **Declarative extensions**: Each server is defined by two JSON files (`server.json` + `installer.json`) — no code required. For advanced cases (e.g., JDT.LS), Java code can be used via SPI
- **Extensible via classpath or bundles**: Contribute extensions as Maven modules on the classpath, or add them at runtime via MCP tools and admin UI
- **IDE settings support**: Loads `.vscode/settings.json` and `.bob/settings.json` to send as LSP `workspace/configuration`, so language servers receive the same settings as in your IDE
- **Admin console**: Web UI to manage servers, monitor workspaces, and view traces at `http://localhost:7654/admin`
- **Any MCP client**: Works with [Claude Desktop](https://claude.ai/download), [Claude Code](https://docs.anthropic.com/en/docs/claude-code), [Bob IDE](https://bob.ibm.com/), and any MCP-compatible assistant

![Admin Console](docs/images/admin-console.png)

## Quick Start

- **[Getting Started (LSP)](docs/getting-started.md)** — Get diagnostics, references, and code navigation working in 5 minutes
- **[Getting Started (DAP)](docs/getting-started-dap.md)** — Debug your code with breakpoints, stepping, and variable inspection

## MCP Tools

### LSP Tools

| Tool | Description |
|------|-------------|
| `get_diagnostics` | Get errors and warnings for a file |
| `get_all_diagnostics` | Get diagnostics for all files in a workspace |
| `goToDefinition` | Jump to where a symbol is defined |
| `goToDeclaration` | Jump to where a symbol is declared |
| `find_references` | Find all usages of a symbol |
| `findImplementations` | Find implementations of an interface/abstract class |
| `get_code_actions` | Get quick fixes and refactoring suggestions |
| `rename` | Rename a symbol across the workspace |
| `searchWorkspaceSymbols` | Search for classes, methods, variables by name |
| `open_document` / `close_document` | Keep a file open for multiple LSP operations |

### DAP Tools

| Tool | Description |
|------|-------------|
| `list_debug_adapters` | List available debug adapters with supported languages |
| `get_debug_templates` | Get launch/attach configuration templates |
| `start_debugging` | Launch or attach a debug session |
| `list_debug_sessions` / `close_debug_session` | Manage debug sessions |
| `set_breakpoint` / `remove_breakpoint` / `list_all_breakpoints` | Manage line breakpoints |
| `set_instruction_breakpoint` / `remove_instruction_breakpoint` / `list_instruction_breakpoints` | Manage instruction-level breakpoints |
| `continue_execution` / `pause_execution` | Continue or pause execution |
| `step_over` / `step_in` / `step_out` | Step through code (statement or instruction granularity) |
| `get_stack_trace` | View the call stack |
| `list_threads` | List all threads in the debugged program |
| `get_scopes` / `get_variables` | Inspect variable scopes and values |
| `get_local_variables` | Shortcut for local variables in the current frame |
| `evaluate_expression` | Evaluate expressions in debug context |
| `get_console_output` | Read program stdout/stderr output |
| `disassemble` | Disassemble instructions at a memory address |
| `detach_from_process` | Detach without terminating the process |
| `get_debug_statistics` | Get statistics about active debug sessions |

### Java Tools (from Java extension)

These tools are inspired by [javalens-mcp](https://github.com/pzalutski-pixel/javalens-mcp).

#### Analysis

| Tool | Description |
|------|-------------|
| `java_get_type_hierarchy` | Get supertypes, super interfaces, and subtypes of a Java type |
| `java_get_call_hierarchy_incoming` | Find all callers of a method |
| `java_get_call_hierarchy_outgoing` | Find all methods called by a method |
| `java_find_annotation_usages` | Find all usages of a Java annotation type |
| `java_find_type_instantiations` | Find all `new Type()` instantiations of a Java type |
| `java_get_complexity_metrics` | Compute cyclomatic complexity and LOC per method |
| `java_analyze_file` | Get comprehensive analysis of a Java file |
| `java_analyze_type` | Get comprehensive analysis of a Java type |
| `java_analyze_method` | Get comprehensive analysis of a Java method |
| `java_analyze_change_impact` | Analyze the ripple effect of changing a symbol |
| `java_analyze_control_flow` | Analyze control flow paths through a Java method |
| `java_analyze_data_flow` | Track data flow through variables and parameters |

#### Navigation

| Tool | Description |
|------|-------------|
| `java_go_to_definition` | Navigate to the definition of a Java symbol |
| `java_get_hover_info` | Get rich hover information (signature, Javadoc, type info) |
| `java_get_javadoc` | Get parsed Javadoc documentation for a symbol |
| `java_get_symbol_info` | Get detailed information about any Java symbol |
| `java_get_enclosing_element` | Get the enclosing method, class, and package for a position |
| `java_get_field_at_position` | Get field information at a specific position |
| `java_get_method_at_position` | Get method information at a specific position |
| `java_get_type_at_position` | Get type information at a specific position |
| `java_get_signature_help` | Get method signature help at a specific position |
| `java_get_super_method` | Find the method that a Java method overrides or implements |
| `java_get_document_symbols` | Get all symbols (types, methods, fields) in a Java file |

#### Code Search

| Tool | Description |
|------|-------------|
| `java_find_references` | Find all references to a Java symbol |
| `java_find_implementations` | Find all implementations of an interface or abstract class |
| `java_find_field_writes` | Find all write accesses to a field |
| `java_find_tests` | Find test methods in a Java file or across the workspace |
| `java_find_affected_tests` | Find tests transitively affected by changes to a symbol |
| `java_find_unused_code` | Find unused code (imports, private fields, methods, local variables) |
| `java_find_unreachable_code` | Find unreachable code (dead code after return/throw) |
| `java_find_reflection_usage` | Find reflection API usage in a Java file |
| `java_suggest_imports` | Find import candidates for an unresolved type name |
| `java_get_type_usage_summary` | Get a comprehensive usage summary for a Java type |
| `java_search_symbols` | Search for Java symbols by name pattern |

#### Reference Search

| Tool | Description |
|------|-------------|
| `java_find_method_references` | Find all references to a method |
| `java_find_casts` | Find all cast expressions to a Java type |
| `java_find_catch_blocks` | Find all catch blocks catching a Java exception type |
| `java_find_instanceof_checks` | Find all instanceof checks for a Java type |
| `java_find_throws_declarations` | Find all throws clause declarations of an exception type |
| `java_find_type_arguments` | Find all type argument usages in generics |

#### Refactoring

| Tool | Description |
|------|-------------|
| `java_rename_symbol` | Rename a Java symbol across the entire project |
| `java_organize_imports` | Organize imports: remove unused and sort |
| `java_extract_method` | Extract a code selection into a new method |
| `java_extract_variable` | Extract an expression into a local variable |
| `java_extract_constant` | Extract an expression into a static final constant field |
| `java_extract_interface` | Extract an interface from a Java class |
| `java_extract_superclass` | Extract a superclass from a Java class |
| `java_inline_method` | Inline a method by replacing call sites with the method body |
| `java_inline_variable` | Inline a local variable by replacing usages with its initializer |
| `java_change_method_signature` | Change a method's signature and update all call sites |
| `java_convert_anonymous_to_lambda` | Convert an anonymous class to a lambda expression |
| `java_encapsulate_field` | Encapsulate a field by generating getter/setter methods |
| `java_introduce_parameter_object` | Bundle method parameters into a parameter object class |
| `java_move_type_to_new_file` | Move a nested/inner type to its own top-level file |
| `java_pull_up` | Pull members up from a subclass into its superclass |
| `java_push_down` | Push members down from a superclass into its subclasses |

#### Code Generation

| Tool | Description |
|------|-------------|
| `java_generate_getters_setters` | Generate getter and/or setter methods for fields |
| `java_generate_constructor` | Generate a constructor from fields |
| `java_generate_to_string` | Generate a toString() method |
| `java_generate_equals_hashcode` | Generate equals() and hashCode() methods |

#### Diagnostics & Fix

| Tool | Description |
|------|-------------|
| `java_validate_syntax` | Quick syntax-only validation (no semantic analysis) |
| `java_get_quick_fixes` | Get available quick fixes for problems at a position |
| `java_apply_quick_fix` | Apply a specific quick fix to resolve a problem |
| `java_diagnose_and_fix` | Diagnose problems and optionally apply safe auto-fixes |
| `java_apply_cleanup` | Apply a code cleanup to a Java file |

#### Code Quality

| Tool | Description |
|------|-------------|
| `java_find_large_classes` | Find classes exceeding size thresholds |
| `java_find_naming_violations` | Find naming convention violations |
| `java_find_possible_bugs` | Find potential bug patterns |
| `java_find_circular_dependencies` | Find circular package dependencies |

#### Framework

| Tool | Description |
|------|-------------|
| `java_get_http_endpoints` | Find all HTTP endpoints (REST API routes) |
| `java_get_jpa_model` | Get the JPA entity model |
| `java_get_di_registrations` | Find dependency injection registrations |

#### Project

| Tool | Description |
|------|-------------|
| `java_get_project_structure` | Get the package hierarchy and file structure |
| `java_get_classpath_info` | Get the classpath entries of a Java project |
| `java_get_type_members` | Get all members of a Java type |
| `java_get_dependency_graph` | Get the import-based dependency graph |

### Extension & Workspace Tools

| Tool | Description |
|------|-------------|
| `list_extensions` | List all installed extensions |
| `add_extension` | Add a new extension (LSP/DAP servers) |
| `remove_extension` | Remove an extension |
| `add_lsp_server` / `add_dap_server` | Add a server to an extension |
| `remove_lsp_server` / `remove_dap_server` | Remove a server |
| `enable_extension` / `disable_extension` | Enable or disable an extension |
| `list_language_servers` | List language servers with status |
| `listWorkspaces` | List active workspaces |

## Bundled Extensions

Each extension groups LSP and/or DAP servers for a language:

| Extension | LSP Server | DAP Server |
|-----------|-----------|------------|
| **Java** | JDT.LS | java-debug |
| **JavaScript** | typescript-language-server | vscode-js-debug |
| **Python** | Pyright | debugpy |
| **Go** | gopls | go-delve |
| **Rust** | rust-analyzer | — |
| **C/C++** | clangd | codelldb |
| **XML** | LemMinX | — |
| **YAML** | yaml-language-server | — |
| **Kotlin** | kotlin-language-server | — |
| **Dart** | dart-lsp | dart-debug |
| **PHP** | Intelephense | vscode-php-debug |
| **Lua** | lua-language-server | — |
| **Dockerfile** | dockerfile-language-server | buildx-dockerfile |
| **MicroProfile** | microprofile-ls | — |
| **Quarkus** | quarkus-ls, qute-ls | — |
| **Liberty** | lemminx-liberty, liberty-ls | — |

## Adding Your Own Extensions

You can add custom LSP/DAP servers in three ways:

1. **Via MCP tools**: Use `add_extension`, `add_lsp_server`, `add_dap_server` — the AI assistant can do it for you
2. **Via Admin UI**: Use the extensions management page at `http://localhost:7654/admin`
3. **Via extension modules**: Create a Maven module with server descriptors

See the **[Extension Guide](docs/extensions.md)** for details.

## Admin Console

Access the admin UI at `http://localhost:7654/admin` to:

- **Manage extensions**: Install, enable, disable, remove extensions and their servers
- **Control servers**: Start, stop, restart LSP and DAP servers
- **Monitor workspaces**: View active workspaces and connected MCP clients
- **View traces**: Debug LSP and MCP communication
- **Debug sessions**: Monitor active debug sessions

See the **[Admin UI Guide](docs/admin-ui.md)** for details.

## Running

### Dev mode (with hot-reload)

```bash
cd dev
../mvnw quarkus:dev
```

The server starts at `http://localhost:7654`. Admin UI at `http://localhost:7654/admin`.

### Configure your MCP client

```json
{
  "mcpServers": {
    "mcp-languagetools": {
      "type": "http",
      "url": "http://localhost:7654/mcp"
    }
  }
}
```

## Project Structure

```
mcp-lsp/
├── core/                        # Core framework (LSP/DAP integration, MCP tools, admin UI)
├── extensions/                  # Language extensions
│   ├── java/                    # Java (JDT.LS + java-debug)
│   ├── xml/                     # XML (LemMinX)
│   ├── javascript/              # JavaScript/TypeScript (ts-language-server + vscode-js-debug)
│   ├── python/                  # Python (Pyright + debugpy)
│   ├── go/                      # Go (gopls + go-delve)
│   ├── rust/                    # Rust (rust-analyzer)
│   ├── c/                       # C/C++ (clangd + codelldb)
│   ├── dart/                    # Dart (dart-lsp + dart-debug)
│   ├── yaml/                    # YAML
│   ├── kotlin/                  # Kotlin
│   ├── php/                     # PHP (Intelephense + vscode-php-debug)
│   ├── lua/                     # Lua
│   ├── dockerfile/              # Dockerfile
│   ├── microprofile/            # MicroProfile
│   ├── quarkus/                 # Quarkus + Qute
│   └── liberty/                 # Liberty
├── admin/                       # Admin UI module
└── dev/                         # Dev distribution (core + all extensions)
```

## Technology Stack

- **[Quarkus](https://quarkus.io/)** — Java framework
- **[Quarkus MCP Server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html)** — MCP implementation
- **[LSP4J](https://github.com/eclipse-lsp4j/lsp4j)** — LSP implementation
- **[DAP4J](https://github.com/nicoschl/dap4j)** — DAP implementation

## Documentation

- **[Getting Started (LSP)](docs/getting-started.md)** — Code validation and navigation
- **[Getting Started (DAP)](docs/getting-started-dap.md)** — Debugging
- **[Extension Guide](docs/extensions.md)** — Add your own servers
- **[Admin UI Guide](docs/admin-ui.md)** — Web console
- **[Bind Mechanism](docs/bind-mechanism.md)** — How servers collaborate
