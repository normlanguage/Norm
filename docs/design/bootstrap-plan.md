# Compiler Bootstrap Plan

The official Java toolchain builds one frontend and one Truffle execution path. Delivered behavior is recorded in the [version index](/versions/); this page defines the structural path toward 1.0.

## Foundation

The root [Gradle Kotlin DSL build](../../build.gradle.kts) pins Java, Truffle, and test dependencies and builds the single compiler product module. `SourceFile`, `SourceSpan`, `Diagnostic`, formatting checks, and CI are shared foundations for later stages.

## Lexer and Parser

The lexer and hand-written parser produce an AST with complete `SourceSpan` information. Error recovery must produce stable diagnostics and let the formatter, LSP, and compiler share the same syntax structure.

## Semantic model

Name resolution, nominal typing, generic constraints, nullable flow analysis, definite assignment, and call binding write to `SemanticModel`. Arguments are mapped to parameters once while preserving source evaluation order.

## Canonical Core

The binder fixes expression types, value and identity categories, call targets, control-flow edges, and reified generic information. `CoreBuilder` converts the result into deterministic Core IR. Definition identity contains canonical content and fixed dependencies, while authoring names and source locations live separately in namespace and occurrence metadata.

## Truffle backend

The lowerer consumes only `CoreArtifact` and creates function call targets, frame slots, control-flow nodes, and interop boundaries. CLI distributions carry the same execution implementation and a platform runtime.

## Acceptance

Each stage includes syntax, semantic, runtime, and real CLI tests. Documentation examples participate in validation, and development entry points and release distributions preserve the same observable behavior.
