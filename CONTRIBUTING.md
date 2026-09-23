# Contributing to Norm

Norm is in the compiler bootstrap stage. Changes should keep the language specification and the Java implementation synchronized, but an unfinished specification feature must not be added to the compiler accidentally.

## Requirements

- JDK 25 and Maven 3.9 or newer.
- Git with LF line endings available for source files.

Build dependencies, the local compiler distribution, and Java formatting are defined by the root Maven reactor. Gradle remains the current public release entry point.

## Build and test

On Unix-like systems:

```shell
./gradlew qualityCheck
mvn -DskipTests package
./cli/compiler/target/norm-runtime/bin/norm --version
```

On Windows:

```powershell
.\gradlew.bat qualityCheck
mvn -DskipTests package
.\cli\compiler\target\norm-runtime\bin\norm.bat --version
```

Run `mvn spotless:check` before submitting Java changes, or `mvn spotless:apply` to format them. Maven `verify` includes the formatting check. CI executes the test suite on both OpenJDK and GraalVM.

## Architecture rules

The [toolchain development standard](https://normlanguage.github.io/Norm/en/design/toolchain-development) is the source of truth for module boundaries, package responsibilities, dependency direction, naming, and verification. Language changes must keep the specification, frontend diagnostics, Truffle lowering, and focused tests synchronized.
