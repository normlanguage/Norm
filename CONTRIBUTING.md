# Contributing to Norm

[简体中文](CONTRIBUTING.zh-CN.md)

Norm is in the compiler bootstrap stage. Changes should keep the language specification and the Java implementation synchronized, but an unfinished specification feature must not be added to the compiler accidentally.

## Requirements

- JDK 25. Use the included Gradle wrapper.
- Git with LF line endings available for source files.

Build dependencies, the local compiler distribution, and Java formatting are defined by the root Gradle Kotlin DSL build.

## Build and test

On Unix-like systems:

```shell
./gradlew qualityCheck
./gradlew :compiler:installRuntimeDist
./build/compiler/norm-runtime/bin/norm --version
```

On Windows:

```powershell
.\gradlew.bat qualityCheck
.\gradlew.bat :compiler:installRuntimeDist
.\build\compiler\norm-runtime\bin\norm.bat --version
```

Run `./gradlew spotlessCheck` before submitting Java changes, or `./gradlew spotlessApply` to format them; use `.\gradlew.bat` on Windows. `qualityCheck` includes formatting and tests. CI runs Java 25 tests and separately verifies Native Image behavior with GraalVM.

## Architecture rules

The [toolchain development standard](https://normlanguage.github.io/Norm/design/toolchain-development) is the source of truth for module boundaries, package responsibilities, dependency direction, naming, and verification. Language changes must keep the specification, frontend diagnostics, Truffle lowering, and focused tests synchronized.
