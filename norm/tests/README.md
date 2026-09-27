# Norm test conventions

[简体中文](README.zh-CN.md)

This directory holds `.norm` tests executed from the user's perspective. Tests are organized by stable domains, not by release versions.

## Categories

Put a single-file test directly in its primary language-domain directory, such as `class`, `value`, `references`, `exceptions`, `reflection`, or `annotations`.

Other directories have these roles:

- `base`: foundational semantics without a separate language domain.
- `algorithms`: algorithms implemented in Norm; subdirectories may group problem sets.
- `projects`: multi-file programs spanning packages, modules, or dependencies.
- Standard library function tests live in [`norm/stdlib/std/tests/test`](../stdlib/std/tests/test), are discovered and run through `@Test`, and follow the [testing API](../../docs/stdlib/testing-api.md).
- `recovery`: incomplete source fixtures for editors and syntax recovery, not executable programs.

When adding or moving tests:

- Choose exactly one domain for the behavior; do not create a second directory hierarchy for the same feature.
- Do not put version numbers, milestones, or `conformance` in paths.
- Do not repeat information already expressed by a parent directory, such as a `cross_package_` prefix under `projects`.
- Use standalone `snake_case` filenames that identify behavior; use numbers only when they are intrinsic to the case.
- Place unit tests for compiler diagnostics in the Java module that produces them. Keep only real `.norm` programs and dedicated recovery fixtures here.

## Single-file programs

An executable program must:

- contain exactly one `main` entry point;
- verify one clear behavior boundary;
- declare nonempty expected output with `std.testing.expectedOutputLine` or `expectedOutputLines`;
- compile and run independently of declarations or execution order in other test files.

[`ProgramExecutionTest`](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ProgramExecutionTest.java) registers single-file directories, and [`NormTestKit`](../../cli/compiler/src/test/java/dev/w0fv1/norm/testing/NormTestKit.java) discovers them recursively. A new top-level domain needs a corresponding test entry; new files within a domain need no registration.

## Project programs

Each project scenario uses this structure:

```text
projects/<scenario>/
├── app/
│   ├── module.norm
│   └── ...
└── dependencies/
    └── <module>/
        ├── module.norm
        └── ...
```

- `<scenario>` uses a `snake_case` name describing the fact under test.
- `app/module.norm` is the sole execution root, and the module name is `app`.
- `app` contains exactly one `main` entry point.
- Create `dependencies` only for a real module-dependency test. Modules there are not executed as separate projects.
- Each scenario owns its source root and dependencies; do not share fixtures between scenarios.

`NormTestKit.projectSuite` automatically discovers first-level directories under `projects`; a new scenario needs no Java registration change.

## Verification

Run the complete Gradle quality gate, including `ProgramExecutionTest` and other tests:

```powershell
.\gradlew.bat qualityCheck
```

Run the focused project-program test:

```powershell
.\gradlew.bat :compiler:test --tests 'dev.w0fv1.norm.truffle.ProgramExecutionTest'
```

Select a test class with `--tests` for a single domain. `StandardLibraryTest` executes standard library tests through the public test runner.

See the [toolchain development guide](../../docs/design/toolchain-development.md) for test architecture and toolchain constraints.
