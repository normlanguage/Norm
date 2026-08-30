# Toolchain Development Standard

This standard defines code organization, dependency direction, and backend rules for the official Java toolchain. See the [implementation strategy](/en/design/implementation-strategy) for technology choices. The language specification remains authoritative for language behavior.

## Repository boundaries

```text
cli/                  command-line product
  compiler/           Java compiler, execution runtime, CLI, and Language Server
  extensions/         editor extensions
norm/stdlib/           standard-library sources written in Norm
norm/tests/            executable Norm acceptance programs
```

`compiler` is the only Gradle and JPMS module. Domain packages provide the layers, cross-layer data uses the strongly typed model owned by the lower layer, and architecture tests prohibit reverse dependencies.

## Core packages

```text
dev.w0fv1.norm.frontend     Compiler, Lexer, Parser, and Analyzer
dev.w0fv1.norm.syntax       tokens and the Syntax AST
dev.w0fv1.norm.semantic     types, symbols, and document semantic indexes
dev.w0fv1.norm.builtin      builtin declarations and intrinsic identities
dev.w0fv1.norm.bound        frontend-internal resolved representation
dev.w0fv1.norm.core         content-addressed Core IR and dependency indexes
dev.w0fv1.norm.core.store   canonical definition storage
dev.w0fv1.norm.diagnostic   diagnostic values and rendering
dev.w0fv1.norm.language     language services over semantic snapshots
dev.w0fv1.norm.value        immutable cross-phase data
```

Inside `compiler`, `execution` owns `ExecutionBackend`, `ExecutionContext`, and structured runtime errors; `platform` owns backend-neutral file, HTTP, and time contracts while `platform.jdk` implements them; `project` owns `ProjectEnvironment`, `ProjectLoader`, and `ProjectLauncher`; and `truffle` owns lowering, executable nodes, runtime representations, and the Norm system-exception bridge.

The required stage dependency constraints are:

```text
frontend ⇏ truffle
core ⇏ frontend, truffle
Lowerer → core
execution → core
project → execution → platform contracts
truffle → project, execution, platform contracts, core
platform.jdk → platform contracts
CLI → project, execution, platform.jdk, frontend, language
```

`⇏` denotes a forbidden dependency. `bound` is confined to the frontend conversion into Core. The lowerer consumes Core and has no dependency on the Syntax AST, `SemanticModel`, or `bound`. The CLI does not access internal Truffle nodes. New packages follow domain ownership and share existing semantic tables.

## CLI packages

```text
dev.w0fv1.norm.cli              JVM entry point
dev.w0fv1.norm.cli.controller   command parsing, routing, and execution
dev.w0fv1.norm.cli.component    version and Language Server components
dev.w0fv1.norm.cli.value        shared CLI data
dev.w0fv1.norm.cli.utils        stateless text utilities
```

Only `Main` may terminate the JVM. Controllers return exit codes, and components do not parse command-line arguments.

Editor features use `core`'s `LanguageService` and immutable semantic snapshots as their sole semantic implementation. Completion ranking, expected types, generic substitution, call parameters, and import candidates are computed in `dev.w0fv1.norm.language`; the Language Server only maps LSP types, and editor extensions only manage lifecycle and editor integration.

## Naming and visibility

- The package already supplies the language context, so types do not repeat a `Norm` prefix. Use domain names such as `Compiler`, `Analyzer`, `Lowerer`, and `ProgramRunner`.
- Only genuine process or extension contracts form an external API. Lexer, Parser, Analyzer, Truffle nodes, and runtime representations remain module-internal.
- `value` contains immutable cross-phase data. Data with a strong domain remains in that domain; the Syntax AST belongs to `syntax`.
- `utils` is limited to static, stateless, independently reusable tools. Lifecycle, I/O, and mutable state do not belong there.
- Each concept has one representation. Parallel legacy ASTs, temporary IRs, and secondary execution paths are prohibited.

## Compilation and execution

```text
SourceFile
  → Lexer
  → Token
  → Parser
  → Syntax.Program
  → Analyzer
  → SemanticModel
  → Binder
  → CoreBuilder
  → CoreCanonicalizer
  → DefinitionStore
  → CompilationOutput
  → Lowerer
  → Truffle executable AST
```

The parser builds syntax only. The analyzer checks names, types, and control flow. The binder freezes validated semantics, CoreBuilder separates canonical definitions from authoring occurrence metadata, and CoreCanonicalizer assigns content identities to recursive groups and their fixed dependencies. The lowerer converts only `CompilationOutput` into executable nodes. See the [compiler architecture](/spec/compiler-design) for the identity boundaries.

One project analysis creates an immutable `CompilationSnapshot`. Diagnostics and language features use per-document projections of the same `SemanticModel`, `SpanIndex`, and `ReferenceIndex`. `CompilerSession` caches unchanged parse results and the standard-library prelude; a new document revision replaces the snapshot atomically.

`ProjectLauncher` and Polyglot Source execution share the `CompilerSession → CompilationOutput → TruffleExecutionBackend` path. `ExecutionContext` carries input, output, arguments, cancellation, and host capabilities as a hidden root argument, allowing artifacts to be reused across independent executions. Guest failures cross the public boundary as structured errors with a stable code, original source location, and guest stack.

Each function owns a `FunctionRootNode` and `CallTarget`. Static function and method calls use `DirectCallNode`; locals use indexed `VirtualFrame` slots; loops use `LoopNode`; return, break, and continue use `ControlFlowException`. Executable nodes receive their exact occurrence origin and `SourceSection` from `CoreAuthoringMap`.

`@TruffleBoundary` is restricted to host I/O and similar slow paths. It must not surround guest-language computation. Value-copy behavior has one runtime implementation; a future copy-on-write representation must preserve observable language semantics.

## Tests

- Add or migrate a failing test before changing implementation.
- Test packages mirror production packages; testing does not justify wider visibility.
- Syntax and execution changes cover diagnostics and the single-file and module programs under `norm/tests`.
- Run affected package tests during development and formatting checks before submission. Full release verification is reserved for releases.
- Backend changes cover both the registered Polyglot language and execution of a real `.norm` file through the CLI.

Acceptance-test domains, layout, naming, discovery entry points, and commands are defined in one place by [`norm/tests/README.md`](https://github.com/w0fv1/norm/blob/main/norm/tests/README.md).

## Documentation ownership

Language behavior belongs in the language specification, implementation structure belongs here, and technology choices belong in the implementation strategy. Other pages link to these sources instead of copying their rules.
