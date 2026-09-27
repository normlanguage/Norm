# Toolchain Development Standard

This standard defines code organization, dependency direction, and backend rules for the official Java toolchain. The [implementation strategy](/design/implementation-strategy) records technology choices; the language specification remains authoritative for language behavior.

## Repository boundaries

```text
cli/                  command-line product
  compiler/           Java compiler, execution runtime, CLI, and Language Server
  extensions/         editor extensions
norm/stdlib/           standard-library sources written in Norm
norm/tests/            executable Norm acceptance programs
```

`compiler` is the only product and JPMS module. The root [Gradle Kotlin DSL](../../build.gradle.kts) also includes build-only [`gradle/build-logic`](../../gradle/build-logic/). Domain packages provide the layers, cross-layer data uses strongly typed models owned by lower layers, and architecture tests prohibit reverse dependencies.

## Domain boundaries

| Package | Responsibility |
| --- | --- |
| `source` / `syntax` | Source identity, locations, and syntax model |
| `abi` / `pattern` | Neutral runtime contracts and pattern-coverage algorithms |
| `semantic` / `builtin` | Semantic model and semantic projection of builtin contracts |
| `frontend` / `bound` | Analysis, resolved semantics, and Core construction |
| `core` / `core.store` | Content-addressed definitions, artifacts, and storage |
| `project` | Project discovery, module resolution, and input snapshots |
| `packages` | Norm repository access, version selection, offline cache, and integrity |
| `lsp` | Protocol translation and exclusive workspace session |
| `application` | Application compilation output, execution preparation, and resource ownership |
| `build` | Application-build use cases, Native plans, toolchains, and delivery |
| `language` / `workspace` | Snapshot queries, project-analysis scheduling, and versioned publication |
| `jvm` | Java type projection, binding planning, and annotation processing |
| `execution` / `platform` | Execution and host-capability contracts |
| `truffle` / `polyglot` | Core execution implementation and Polyglot integration |
| `diagnostic` / `value` | Diagnostics and remaining cross-phase values |

[DependencyArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/DependencyArchitectureTest.java) is the executable source for dependency direction, including the package-level cycle ban. `bound` is consumed only by the frontend; Core does not depend on frontend semantics or the builtin catalog. `project`, `jvm`, and `truffle` do not depend back on application orchestration. See the [compiler architecture](/spec/compiler-design) for complete stages and lifecycles.

## CLI packages

```text
dev.w0fv1.norm.cli              JVM entry point
dev.w0fv1.norm.cli.controller   command parsing, routing, and execution
dev.w0fv1.norm.cli.component    version component
dev.w0fv1.norm.cli.value        shared CLI data
dev.w0fv1.norm.cli.utils        stateless text utilities
```

Only `Main` may terminate the JVM; `runtime.NativeApplicationMain` is the native application's process entry. Controllers return exit codes, and components do not read command-line arguments.

[`ApplicationBuilder`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/build/ApplicationBuilder.java) is the application-build entry. CLI parses arguments, assembles services, and presents progress and results. `build` does not depend on CLI, Workspace, or Truffle nodes, and lower layers do not depend back on `build`. [DependencyArchitectureTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/DependencyArchitectureTest.java) and the [`build` tests](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/test/java/dev/w0fv1/norm/build) enforce these constraints.

[`LanguageServerLauncher`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/lsp/LanguageServerLauncher.java) launches LSP. CLI transfers the workspace, and the session closes it and returns an exit code. [`verify-lsp.mjs`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/verify-lsp.mjs) exercises the real protocol.

Editor features use `language.LanguageService` and immutable semantic snapshots as their sole semantic implementation. Completion ranking, expected types, generic substitution, call parameters, and import candidates are computed in `dev.w0fv1.norm.language`. Workspace schedules project analysis and diagnostic publication; the Language Server maps LSP types, and editor extensions manage only lifecycle and editor integration.

## Naming and visibility

- `dev.w0fv1.norm` already supplies the language context, so type names do not repeat a `Norm` prefix. Use domain names such as `Compiler`, `Analyzer`, `Lowerer`, and `ApplicationRunner`.
- Only genuine process or extension contracts form external APIs. Lexer, Parser, Analyzer, Truffle nodes, and runtime representations remain module-internal.
- `value` holds immutable cross-phase data only. Data with a clear domain remains in that domain; the Syntax AST belongs to `syntax`.
- `utils` holds only static, stateless, independently reusable tools. Lifecycles, I/O, and mutable state do not belong there.
- Each concept has one model. Parallel legacy ASTs, temporary IRs, and second execution paths are prohibited.

## Compilation and execution stages

The [compiler architecture](/spec/compiler-design) and its code entries define stage, artifact, and ownership details. Application entry uses `ApplicationCompiler`, `CompiledApplication`, and `ApplicationRunner`; editor entry uses Workspace. Callers close application artifacts they own, and every run owns independent runtime resources.

`ResolvedCall` is the single result of a bound call and is reused by language services and binding. `TypeSyntaxParser` handles incomplete type inputs rather than introducing a second type grammar. Builtin signatures are declared only in `stdlib-abi.json`; semantic objects and Core validation contracts derive from it.

`RuntimeValues` owns value representations, copying, equality, and hashing; `RuntimeInvocation` owns call preparation, and `RuntimeText` owns Unicode text operations. Truffle nodes must not capture external resources belonging to one run. See the [system runtime architecture](/design/system-runtime) for resource contracts.

## ABI code generation

[`BuiltinAbiGenerator`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/codegen/BuiltinAbiGenerator.java) is build-time code in `gradle/build-logic`, not part of the product module. The root [Gradle Kotlin DSL](../../build.gradle.kts) declares the build-tool and compiler lifecycle; `stdlib-abi.json` remains the sole declaration source for builtin signatures. The same build entry runs Java formatting and `qualityCheck`.

[`BuildMetadataGenerator`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/codegen/BuildMetadataGenerator.java) in the same build logic generates version metadata; the [compiler Gradle configuration](../../cli/compiler/build.gradle.kts) supplies version, GraalVM, and output directory.

[`ToolchainArtifactCatalogGenerator`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/ToolchainArtifactCatalogGenerator.java) generates and verifies the toolchain dependency catalog. Gradle tasks supply the actual resolved graph and JAR files. Build logic calls the same assembler and catalog generator and is not shipped with the product; compiler source is compiled with Java 25.

[`BuiltinAbiGeneratorTest`](../../gradle/build-logic/src/test/java/dev/w0fv1/norm/codegen/BuiltinAbiGeneratorTest.java) verifies generated bytes and fingerprints. [`verify-codegen.mjs`](../../cli/compiler/scripts/verify-codegen.mjs) verifies clean builds, input changes, rebuilding, and release isolation without changing the schema in a user's workspace.

## Tests

- Add or migrate a failing test before changing implementation.
- Test packages mirror production packages; testing does not justify wider visibility.
- Syntax and execution changes cover diagnostics and the single-file and module programs under `norm/tests`.
- Run affected package tests during development and formatting checks before submission. Full release verification is reserved for releases.
- Backend changes cover both the registered Polyglot language and execution of a real `.norm` file through the CLI.

[`norm/tests/README.md`](https://github.com/normlanguage/Norm/blob/main/norm/tests/README.md) defines acceptance-test domains, layout, naming, discovery entries, and commands in one place.

## Documentation synchronization

Language behavior belongs in the language specification, implementation structure here, and technology choices in the implementation strategy. Other pages link to these sources rather than copying their rules.

`project.ProjectLoader` is the project-loading facade. `ProjectModuleSources`, `ProjectDependencyGraph`, `ArchivedModuleLoader`, and `JarBindingPreparer` separately own source loading, dependency graphs, archive caching, and binding preparation. [ProjectLoadingBoundaryTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ProjectLoadingBoundaryTest.java) verifies captured inputs, failure retries, and boundary contracts.

## Local acceptance and measurement entries

The Windows CLI and extension use root Gradle `gradlew.bat :compiler:installRuntimeDist`; `:compiler:packageDistribution` writes formal assets to root `build/distributions/`. [resolve-toolchain.ps1](../../cli/compiler/scripts/resolve-toolchain.ps1) verifies installation paths and compiler digests. Root Gradle `printNormVersion` prints the default version or `-PnormVersion` override, while formal releases take versions only from SemVer tags. CLI, extension, and GUI acceptance must record actual artifact identity, not merely compare version numbers.

In a network-restricted build, verified reachability metadata must still be supplied. Only [ReachabilityMetadataArchive](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/ReachabilityMetadataArchive.java) declares the archive source and digest. Other build tools, plugins, and Java dependencies also need prior supply. The official distribution-source-package route has separate [source-build acceptance](/design/distribution-source-build).

The self-contained installation tree is at `build/compiler/norm-runtime`: [`RuntimeModuleAssembler`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/RuntimeModuleAssembler.java) assembles `lib`, [`RuntimeLauncherGenerator`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/RuntimeLauncherGenerator.java) generates `bin`, and [`RuntimeImageGenerator`](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/packaging/RuntimeImageGenerator.java) generates the bundled JDK through `jlink`. The self-hosted APT/RPM packages use application-private dependencies; they are not official source packages using distribution system libraries.

Focused tests in network-restricted environments can use an isolated local Java dependency repository; [MavenTestRepository](../../cli/compiler/src/test/java/dev/w0fv1/norm/testing/MavenTestRepository.java) loads fixtures. Cache hits do not constitute acceptance of a clean network or distribution-managed system dependencies.

[compare-compiler.ps1](../../cli/compiler/scripts/compare-compiler.ps1) alternates two complete dependency directories with the same Java, arguments, and source. It records compilation, incremental analysis, execution time, main-thread allocation, and observed peak working set. [CompilerBenchmark](../../cli/compiler/src/test/java/dev/w0fv1/norm/testing/CompilerBenchmark.java) defines metrics and warmup counts. Main-thread allocation is not process-wide allocation, and peak working set includes startup and warmup; sample results cannot be generalized into a toolchain-wide performance claim.
