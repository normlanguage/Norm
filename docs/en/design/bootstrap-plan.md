# Compiler Bootstrap Plan

The official Java toolchain builds one frontend and one GraalVM/Truffle execution path. Delivered behavior is recorded in the [version index](/en/versions/); this page defines the structural path toward 1.0.

## Foundation

The single Gradle compiler module owns pinned Java and Truffle dependencies, source locations, diagnostics, formatting, tests, and CI.

## Frontend

The lexer and parser produce a source-mapped AST. Name resolution, nominal typing, generic constraints, nullable flow analysis, definite assignment, and call binding produce a shared SemanticModel.

## Canonical Core

The binder fixes expression types, value and identity categories, call targets, control-flow edges, and reified generic information. CoreBuilder converts the result into deterministic Core IR. Definition identity contains canonical content and fixed dependencies, while names and source locations live in the namespace and occurrence metadata.

## Truffle backend

Lowering consumes `CoreCompilation` and creates call targets, frame slots, control-flow nodes, and interop boundaries. Native Image packages the same CLI and runtime.

## Acceptance

Each stage includes syntax, semantic, runtime, and real CLI tests. Documentation examples participate in validation, and JVM and Native Image execution preserve the same observable behavior.
