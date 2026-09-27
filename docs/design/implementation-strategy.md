# Implementation Strategy Decision

Status: **Accepted**

This decision records Norm's implementation stack and applies to the official compiler, runtime, execution backend, CLI, and core development tools. It is not part of the language syntax or type system and does not restrict independent third-party implementations.

## Decision

1. **Java implements the entire core toolchain.** The lexer, parser, AST, name resolution, type checker, content-addressed Core IR, formatter, shared LSP components, package tooling, and CLI use Java.
2. **Truffle is the sole official execution backend.** Lowering accepts canonical Core and produces the Truffle execution representation.
3. **CLI distributions bundle a Java runtime and manage Native Image.** Each release contains the compiler, its dependencies, and a platform runtime. `norm setup` installs a pinned GraalVM Community toolchain with content-integrity verification. Users do not configure Java or GraalVM, while independently published Java bindings and annotation processors remain dynamically loadable.
4. **Zig is not part of the core implementation.** Core, the CLI, and standard-library platform adapters contain no Zig code or Zig/Java FFI boundary.

## Project boundaries

```text
cli/                    command-line product
  compiler/             Java compiler, execution runtime, CLI, and Language Server
  extensions/           editor extensions
norm/                   standard library and language sources written in Norm
```

The official Java product implementation is one JPMS module in the root Gradle Kotlin DSL build, which also includes isolated build logic. The frontend, Core, execution, project, platform, and CLI domains remain separate packages, with architecture tests enforcing their dependency direction. Physical modules do not duplicate those boundaries. The standard library's public API is written in Norm. The [toolchain development standard](/design/toolchain-development) is authoritative for package responsibilities, dependency direction, and verification.

## Build and distribution

- The root Gradle Kotlin DSL builds the sole compiler product module.
- Java toolchain and Truffle versions are pinned in the repository.
- Unit and Truffle integration tests use the same JVM execution model.
- Release jobs use `jlink` for self-contained `norm` distributions on each platform.
- `norm build` targets Native Image by default; `--jvm` is an explicit development and compatibility target.
- JARs are internal build artifacts, not the primary installation interface for users.

## Why Zig is not the core toolchain

Truffle's language, Node, Interop, and Context APIs live on the Java side. A Zig frontend would require a separate C ABI, memory ownership scheme, and AST/IR serialization protocol. Source locations, diagnostics, and generic metadata would also be copied across languages. These costs do not improve Norm's language semantics or first-release delivery speed.

Zig may be used in future experiments or external tools unrelated to the core implementation, but it cannot become a required dependency in the official build, execution, or release chain. Changing this decision requires a new project proposal covering migration cost, debugging, and ecosystem compatibility.

## Non-goals

- No independent native compiler backend.
- No simultaneous Truffle AST and second execution engine.
- No complete standard library, web platform, or package registry in the first stage.
- No sacrifice of runtime dynamic loading merely to reduce tool size.

Continue with the [compiler bootstrap plan](/design/bootstrap-plan).
