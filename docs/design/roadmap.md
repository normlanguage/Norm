# Roadmap

The roadmap targets Norm 1.0 and follows the [implementation strategy](/design/implementation-strategy): the core toolchain uses Java, Truffle is the execution backend, and the CLI is delivered with a bundled Java runtime. Zig is outside the core implementation.

Delivered versions are recorded in the [version index](/versions/) rather than duplicated here.

## Language frontend

Stabilize lexical rules, parsing, name resolution, the nominal type system, nullability, definite assignment, named arguments, and control-flow expressions. The syntax tree, SemanticModel, diagnostics, and language services share one set of declaration and type information.

## Objects and types

Complete class inheritance, values, `ref<T>`, and the rest of the 1.0 object model, backed by conformance tests.

## Core IR and execution

Keep canonical Core as the sole backend input while improving the definition store, dependency index, artifact cache, and observability. The CLI, Language Server, tests, and standalone Java bindings share one JVM execution model.

## Modules and standard library

Complete the remaining core 1.0 standard-library APIs for collections, I/O, time, concurrency, and Java interoperability.

## Tools and releases

Complete incremental LSP support, formatter, debugging and profiling interfaces, package manager, Registry, compatibility policy, and release process. The 1.0 release candidate freezes the language specification, diagnostic codes, core standard-library APIs, and toolchain protocols.
