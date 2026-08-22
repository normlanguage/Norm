# Toolchain Development Standard

This standard defines code organization, dependency direction, and backend rules for the official Java toolchain. See the [implementation strategy](/en/design/implementation-strategy) for technology choices. The language specification remains authoritative for language behavior.

## Repository boundaries

```text
tool/core/             compiler frontend, diagnostics, execution API, and Truffle backend
tool/cli/app/          command-line and Language Server lifecycle
tool/cli/extensions/   editor extensions
norm/stdlib/           standard-library sources written in Norm
norm/tests/            executable Norm acceptance programs
```

Compiler, runtime, and Truffle code remain in one `core` Gradle module so syntax, type information, and source locations have one representation.

## Core packages

```text
dev.w0fv1.norm.frontend     Compiler, Lexer, Parser, and Analyzer
dev.w0fv1.norm.syntax       tokens and the Syntax AST
dev.w0fv1.norm.semantic     types, symbols, and document semantic indexes
dev.w0fv1.norm.builtin      builtin declarations and intrinsic identities
dev.w0fv1.norm.bound        immutable Bound IR consumed by the backend
dev.w0fv1.norm.diagnostic   diagnostic values and rendering
dev.w0fv1.norm.language     language services over semantic snapshots
dev.w0fv1.norm.execution    public execution API, context, and structured errors
dev.w0fv1.norm.truffle      lowering, executable nodes, and runtime representation
dev.w0fv1.norm.value        immutable cross-phase data
```

The required stage dependency constraints are:

```text
frontend ⇏ truffle
bound ⇏ frontend, truffle
Lowerer → bound
CLI → core public API
```

`⇏` denotes a forbidden dependency. The lowerer must not depend on the Syntax AST or `SemanticModel`, and the CLI must not access internal Truffle nodes. New packages follow domain ownership; they must not duplicate types or semantic tables to evade these constraints.

## CLI packages

```text
dev.w0fv1.norm.cli              JVM entry point
dev.w0fv1.norm.cli.controller   command parsing, routing, and execution
dev.w0fv1.norm.cli.component    version and Language Server components
dev.w0fv1.norm.cli.value        shared CLI data
dev.w0fv1.norm.cli.utils        stateless text utilities
```

Only `Main` may terminate the JVM. Controllers return exit codes, and components do not parse command-line arguments.

## Naming and visibility

- The package already supplies the language context, so types do not repeat a `Norm` prefix. Use domain names such as `Compiler`, `Analyzer`, `Lowerer`, and `ProgramRunner`.
- Only external APIs are public. Lexer, Parser, Analyzer, Truffle nodes, and runtime representations remain module-internal.
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
  → BoundProgram
  → ExecutionBackend
  → Lowerer
  → Truffle executable AST
```

The parser builds syntax only. The analyzer checks names, types, and control flow. The binder freezes validated semantics into Bound IR. The lowerer only converts Bound IR into executable nodes. Runtime code must not resolve declarations by name or interpret the Syntax AST.

One project analysis creates an immutable `CompilationSnapshot`. Diagnostics and language features use per-document projections of the same `SemanticModel`, `SpanIndex`, and `ReferenceIndex`. Unchanged parse results and the standard-library prelude are cached by `CompilationEnvironment`; a new document revision replaces the snapshot atomically.

`ProgramRunner` and Polyglot Source execution share the `Compiler → BoundProgram → TruffleExecutionBackend` path. `ExecutionContext` carries input, output, arguments, and cancellation explicitly. Guest failures cross the public boundary as structured errors with a stable code, original source location, and guest stack.

Each function owns a `FunctionRootNode` and `CallTarget`. Static function and method calls use `DirectCallNode`; locals use indexed `VirtualFrame` slots; loops use `LoopNode`; return, break, and continue use `ControlFlowException`. Executable nodes retain `SourceSection` information.

`@TruffleBoundary` is restricted to host I/O and similar slow paths. It must not surround guest-language computation. Value-copy behavior has one runtime implementation; a future copy-on-write representation must preserve observable language semantics.

## Tests

- Add or migrate a failing test before changing implementation.
- Test packages mirror production packages; testing does not justify wider visibility.
- Syntax and execution changes cover diagnostics and the single-file programs under `norm/tests`.
- Run affected module tests during development and formatting checks before submission. Full release verification is reserved for releases.
- Backend changes cover both the registered Polyglot language and execution of a real `.norm` file through the CLI.

```powershell
.\gradlew.bat :core:spotlessApply :core:test
.\gradlew.bat :cli:test
.\gradlew.bat :cli:run --args="run docs/examples/hello.norm"
```

## Documentation ownership

Language behavior belongs in the language specification, implementation structure belongs here, and technology choices belong in the implementation strategy. Other pages link to these sources instead of copying their rules.
