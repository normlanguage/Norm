# Implementation Strategy Decision

Status: **Accepted**

This decision applies to the official Norm compiler, runtime, execution backend, CLI, and core development tools. It does not restrict independent third-party implementations.

## Decision

1. **Java implements the entire core toolchain.** The lexer, parser, AST, name resolution, type checker, content-addressed Core IR, formatter, shared LSP components, package tooling, and CLI use Java.
2. **Truffle/GraalVM is the sole official execution backend.** Lowering accepts canonical Core and produces the Truffle execution representation.
3. **Native Image produces the standalone CLI.** Releases provide a native `norm` executable; the JVM form remains available for development and debugging.
4. **Zig is not part of the core implementation.** Core, the CLI, and standard-library platform adapters contain no Zig code or Zig/Java FFI boundary.

## Module boundaries

```text
tool/core   compiler frontend, execution model, and Truffle backend
tool/cli    command line, Language Server, and editor extensions
norm        standard library and language sources written in Norm
```

The compiler, runtime, and Truffle backend share one `core` Gradle module. The CLI consumes its public Java API. The standard library exposes Norm APIs while platform capabilities remain in core. The [toolchain development standard](/en/design/toolchain-development) is authoritative for package responsibilities, dependency direction, and verification.

The build uses a Gradle multi-project layout and pins the Java toolchain, GraalVM, and Truffle versions. A change to this decision requires a new project proposal with migration, debugging, and Native Image impact analysis.

Continue with the [compiler bootstrap plan](/en/design/bootstrap-plan).
