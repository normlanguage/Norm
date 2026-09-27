# Technical plan

The official Norm implementation uses Java and the Truffle execution backend. The CLI is delivered as a platform distribution with a bundled Java runtime. See the [implementation strategy](/design/implementation-strategy) for the complete decision.

## 1. Java frontend

The core compiler handles lexing, parsing, the AST, name resolution, the nominal type system, null safety, overload resolution, generics and variance, definite assignment, and exhaustive switches. It emits content-addressed Core IR.

Parsing uses handwritten recursive descent and Pratt expression parsing. Syntax and semantic snapshots retain SourceSpan; canonical Core relates back to source through separate authoring-occurrence metadata.

## 2. Truffle backend

The `truffle` package in `compiler` implements the Norm language, execution nodes, interoperability, instrumentation, runtime values, and reified generic metadata.

Truffle only consumes type-checked IR; it does not resolve names or infer types again. The official project maintains no second execution backend.

## 3. Standard library and real applications

After the language loop is complete, implement collections, text, time, I/O, JSON, HTTP, SQL, testing, and logging in sequence. Platform capabilities may initially use Java/JDK/JDBC adapters, but public Norm APIs must not expose host implementation details. System capabilities, exception conversion, host values, and resource lifetimes follow the [system runtime architecture](/design/system-runtime).

## 4. Self-contained distributions

The CLI uses the same JVM application in development and releases. Formal distributions use `jlink` to build a platform runtime and package it with the compiler, dependencies, and launcher. Release tests verify ordinary programs, dynamic Java bindings, annotation processors, diagnostics, exit codes, and LSP behavior.

## 5. Build system

The root [Gradle Kotlin DSL](../../build.gradle.kts) builds the single compiler product module and pins the Java toolchain and Truffle versions. Zig is outside the compiler and standard-library platform adapters.

See the [compiler bootstrap plan](/design/bootstrap-plan) for the implementation sequence.
