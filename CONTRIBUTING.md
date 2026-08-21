# Contributing to Norm

Norm is in the compiler bootstrap stage. Changes should keep the language specification and the Java implementation synchronized, but an unfinished specification feature must not be added to the compiler accidentally.

## Requirements

- A JDK capable of running Gradle 9.7.1. JDK 25 is preferred.
- Git with LF line endings available for source files.

The Gradle toolchain resolver downloads a matching JDK 25 when necessary. The official backend dependencies are pinned in `gradle/libs.versions.toml`.

## Build and test

On Unix-like systems:

```shell
./gradlew qualityCheck
./gradlew :cli:run --args="--version"
```

On Windows:

```powershell
.\gradlew.bat qualityCheck
.\gradlew.bat :cli:run --args="--version"
```

Run `spotlessApply` before submitting Java changes. CI executes the test suite on both OpenJDK and GraalVM.

## Architecture rules

The [toolchain development standard](https://w0fv1.github.io/norm/en/design/toolchain-development) is the source of truth for module boundaries, package responsibilities, dependency direction, naming, and verification. Language changes must keep the specification, frontend diagnostics, Truffle lowering, and focused tests synchronized.
