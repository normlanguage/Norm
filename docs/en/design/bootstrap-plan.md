# Compiler Bootstrap Plan

The official Java toolchain builds one frontend and one GraalVM/Truffle execution path. Delivered behavior is recorded in [release notes](/en/versions/0.1); this page defines the structural path toward 1.0.

## Foundation

The Gradle build owns core and CLI modules, pinned Java and Truffle dependencies, source locations, diagnostics, formatting, tests, and CI.

## Frontend

The lexer and parser produce a source-mapped AST. Name resolution, nominal typing, generic constraints, nullable flow analysis, definite assignment, and call binding produce a shared SemanticModel.

## Typed IR

Typed IR fixes expression types, value and identity categories, call targets, control-flow edges, and reified generic information. It is the only frontend-to-backend boundary.

## Truffle backend

Lowering consumes Typed IR and creates call targets, frame slots, control-flow nodes, and interop boundaries. Native Image packages the same CLI and runtime rather than introducing another backend.

## Acceptance

Each stage includes syntax, semantic, runtime, and real CLI tests. Documentation examples participate in validation, and JVM and Native Image execution preserve the same observable behavior.
