# Contributing to Norm

Norm is in the compiler bootstrap stage. Changes should keep the language specification and the Java implementation synchronized, but an unfinished specification feature must not be added to the compiler accidentally.

## Requirements

- JDK 25. Use the included Maven wrapper; system Maven 3.9 or newer is supported for distribution builds.
- Git with LF line endings available for source files.

Build dependencies, the local compiler distribution, and Java formatting are defined by the root Maven reactor.

## Build and test

On Unix-like systems:

```shell
./mvnw verify
./mvnw -DskipTests package
./cli/compiler/target/norm-runtime/bin/norm --version
```

On Windows:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests package
.\cli\compiler\target\norm-runtime\bin\norm.bat --version
```

Run `./mvnw spotless:check` before submitting Java changes, or `./mvnw spotless:apply` to format them; use `.\mvnw.cmd` on Windows. Maven `verify` includes the formatting check. CI runs Maven tests with JDK 25 and separately verifies Native Image behavior with GraalVM.

## Architecture rules

The [toolchain development standard](https://normlanguage.github.io/Norm/en/design/toolchain-development) is the source of truth for module boundaries, package responsibilities, dependency direction, naming, and verification. Language changes must keep the specification, frontend diagnostics, Truffle lowering, and focused tests synchronized.
