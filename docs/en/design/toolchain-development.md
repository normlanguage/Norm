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
dev.w0fv1.norm.diagnostic   diagnostic values and rendering
dev.w0fv1.norm.execution    public execution entry point
dev.w0fv1.norm.truffle      lowering, executable nodes, and runtime representation
dev.w0fv1.norm.value        immutable cross-phase data
dev.w0fv1.norm.utils        stateless shared utilities
```

The primary dependency direction is:

```text
frontend  → syntax, diagnostic, value
execution → truffle, value
truffle   → frontend, syntax, value
diagnostic → value
```

The frontend must not depend on Truffle. Syntax and value packages must not depend on compiler behavior. The CLI uses exported core APIs and does not access internal Truffle nodes.

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
  → TypedProgram
  → Lowerer
  → Truffle executable AST
  → ProgramRunner
```

The parser builds syntax only. The analyzer checks names, types, and control flow. The lowerer converts the checked program into executable nodes. Runtime code must not resolve declarations by name or interpret the Syntax AST.

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
