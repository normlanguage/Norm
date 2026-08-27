# Norm

<p align="center"><img src="docs/public/brand/norm.svg" alt="Norm Logo" width="144"></p>

Norm is a specification and compiler-bootstrap repository for a statically typed, application-oriented programming language.

Norm prioritizes explicit behavior, predictable semantics, strong typing, value semantics by default, and practical application development.

## Status

**Active development.** Norm source remains the authoring source while the compiler uses deterministic, content-addressed Core IR for fixed definition identities, dependency tracking, persistent definition storage, and Truffle artifact reuse. The [current implementation contract](https://w0fv1.github.io/norm/en/versions/0.13) defines this boundary.

## Build

```shell
./gradlew qualityCheck
./gradlew :cli:run --args="--version"
./gradlew :cli:run --args="run docs/examples/hello.norm"
```

On Windows, use `gradlew.bat`. Gradle selects the pinned Java 25 toolchain automatically.

Tagged releases provide a standalone `norm` executable and a VS Code extension that contains the matching executable. See the [release process](https://w0fv1.github.io/norm/design/release-process) for supported platforms and acceptance requirements.

## Documentation

The complete handbook lives in `docs/` and is built with VitePress.

After GitHub Pages deployment, the documentation is available at:

**https://w0fv1.github.io/norm/**

## Repository layout

```text
tool/core/                    Compiler frontend and canonical Core IR
tool/execution-api/           Backend-neutral execution contract
tool/truffle-backend/         Truffle backend and runtime composition
tool/cli/app/                 command-line application and language server
tool/cli/extensions/          editor extensions
norm/stdlib/                  standard library written in Norm
norm/tests/                   executable Norm test programs
docs/                         language handbook and examples
```

## Implementation strategy

Norm's official toolchain is implemented in Java. GraalVM/Truffle is the sole official execution backend, and GraalVM Native Image produces the standalone `norm` CLI. Zig is not part of the compiler, runtime, backend, CLI, or core standard-library adapters.

The frontend produces canonical Core IR before backend lowering. Authoring names and source metadata remain separate from semantic definition identity, and Truffle consumes Core as its only program input. See the [compiler architecture](https://w0fv1.github.io/norm/spec/compiler-design) and [implementation strategy](https://w0fv1.github.io/norm/design/implementation-strategy).
