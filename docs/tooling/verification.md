---
title: Checks and Tests
description: Norm static checks, focused tests, and structured feedback
---

# Checks and Tests

```bash
norm check path/to/module --format json
norm check path/to/source.norm
norm test path/to/module --filter package.function --format json
```

`check` analyzes production and test sources without requiring `main` or running the business entry point or tests. Module configuration is still evaluated, and dependencies resolve under ordinary project rules. A directory entry point must directly contain `module.norm`; a file entry point must be a production or test source.

`test` compiles and runs tests; `--filter` limits the package or function. See the [Testing API](/stdlib/testing-api) for test declarations and filtering semantics.

## Output contract

Both commands use text output by default. `--format json` reserves stdout for one JSON object. Logs emitted through a Norm execution context during tests go to stderr; a Java library that writes directly to process stdout bypasses that routing. Consume the CLI output directly rather than treating launch-tool logs such as Gradle output as command JSON.

The machine result includes protocol and toolchain versions, command, status, exit code, and diagnostics. Completed tests include statistics and failures; failed commands include the reason. [CommandReportWriter](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/component/CommandReportWriter.java) is the single implementation entry point for fields; see [CommandReport](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/value/CommandReport.java) for status and exit code. [StructuredCommandsTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/StructuredCommandsTest.java) accepts the contract.

Diagnostic positions use document URIs and zero-based UTF-16 character offsets with exclusive ends. Runtime exception lines and columns remain one-based. Error codes, related diagnostics, and notes come from the compiler, not parsed text.

When no tests are found, the command returns a failing exit code and `no_tests` status. Compile errors and test failures may share an exit code; machine consumers should read `status` for the specific outcome.

A result applies to the inputs captured and analyzed by that invocation, not sources edited later. Current results lack a complete input snapshot identity and cannot serve as a pre-edit version token across calls.
