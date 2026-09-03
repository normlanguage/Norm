# Norm

<p align="center"><img src="docs/public/brand/norm.svg" alt="Norm Logo" width="144"></p>

Norm is a specification and compiler-bootstrap repository for a statically typed, application-oriented programming language.

Norm uses distinct language constructs for distinct semantics: classes express identity, values express data, enums express alternatives, interfaces express capability, and refs express controlled aliasing.

## Status

**Active development.** Norm source remains the authoring source while the compiler uses deterministic, content-addressed Core IR for fixed definition identities, dependency tracking, persistent definition storage, and Truffle artifact reuse. The [current implementation contract](https://normlanguage.github.io/Norm/en/versions/0.16) defines this boundary.

## Build

```shell
./gradlew qualityCheck
./gradlew :compiler:run --args="--version"
./gradlew :compiler:run --args="run docs/examples/hello.norm"
```

On Windows, use `gradlew.bat`. Gradle selects the pinned Java 25 toolchain automatically.

Tagged releases provide a standalone `norm` executable and a VS Code extension that contains the matching executable. See the [release process](https://normlanguage.github.io/Norm/design/release-process) for supported platforms and acceptance requirements.

## Documentation

The VitePress site separates the continuous Language Tour, precise Language Reference, current standard-library API, tooling and compiler design, and release status. Its primary examples are compiled, executed, and checked against companion output files.

After GitHub Pages deployment, the documentation is available at:

**https://normlanguage.github.io/Norm/**

## Repository layout

```text
cli/                         command-line product
  compiler/                  Java compiler, runtime, CLI, and language server
  extensions/                editor extensions
norm/stdlib/                  standard library written in Norm
norm/tests/                   executable Norm test programs
docs/                         documentation site
norm/tests/docs/              executable documentation examples
```

## Implementation strategy

Norm's official compiler is implemented in Java as one physical module whose packages preserve the compilation and execution boundaries. GraalVM/Truffle is the sole official execution backend, and GraalVM Native Image produces the standalone `norm` CLI. Zig is not part of the compiler or standard-library platform adapters.

The frontend produces canonical Core IR before backend lowering. Authoring names and source metadata remain separate from semantic definition identity, and Truffle consumes Core as its only program input. See the [compiler architecture](https://normlanguage.github.io/Norm/spec/compiler-design) and [implementation strategy](https://normlanguage.github.io/Norm/design/implementation-strategy).
