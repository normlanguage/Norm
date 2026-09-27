# Norm

<p align="center"><img src="docs/public/brand/norm.svg" alt="Norm Logo" width="144"></p>

[简体中文](README.zh-CN.md)

Norm is a specification and compiler-bootstrap repository for a statically typed, application-oriented programming language.

Norm uses distinct language constructs for distinct semantics: classes express identity, values express data, enums express alternatives, interfaces express capability, and refs express controlled aliasing.

## Status

License: [MPL-2.0](LICENSE). Scope and source availability: [LICENSING.md](LICENSING.md).

**Active development.** Norm source remains the authoring source while the compiler uses deterministic, content-addressed Core IR for fixed definition identities, dependency tracking, persistent definition storage, and Truffle artifact reuse. The [version index](https://normlanguage.github.io/Norm/versions/) identifies the current implementation contract.

## Build

```shell
./gradlew :compiler:installRuntimeDist
./build/compiler/norm-runtime/bin/norm --version
./build/compiler/norm-runtime/bin/norm run cli/compiler/scripts/fixtures/hello.norm
```

The Gradle wrapper builds with Java 25. On Windows, run `.\gradlew.bat :compiler:installRuntimeDist` and use `build\compiler\norm-runtime\bin\norm.bat` as the CLI. Release assets are written to `build/distributions`; see the [source-build design](docs/design/distribution-source-build.md).

Tagged releases provide a self-contained `norm` distribution and a VS Code extension that contains the matching distribution. See the [release process](https://normlanguage.github.io/Norm/design/release-process) for supported platforms and acceptance requirements.

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

Norm's official compiler is implemented in Java as one physical module whose packages preserve the compilation and execution boundaries. Truffle is the sole official execution backend. Releases bundle a platform runtime so the CLI can load independently published Java libraries without requiring a system Java installation. Zig is not part of the compiler or standard-library platform adapters.

The frontend produces canonical Core IR before backend lowering. Authoring names and source metadata remain separate from semantic definition identity, and Truffle consumes Core as its only program input. See the [compiler architecture](https://normlanguage.github.io/Norm/spec/compiler-design) and [implementation strategy](https://normlanguage.github.io/Norm/design/implementation-strategy).

## Repository scope

This repository maintains the Norm language, standard library, compiler and CLI, VS Code extension, documentation, and tests.

Application examples and end-to-end acceptance tests live in [examples](https://github.com/normlanguage/examples). Adapter packages are maintained, tested, and released independently under the [normlanguage](https://github.com/normlanguage) organization. The compiler provides generic package resolution, Java interoperability, and Native Image integration.
