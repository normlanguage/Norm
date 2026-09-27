---
title: Agent Task Benchmark
description: Independent submissions, behavior acceptance, and traceable execution evidence
---

# Agent Task Benchmark

The benchmark separates task work from scoring through editable projects and independent acceptance. Tasks cover feature additions, type-error fixes, semantic renaming, API changes, and test additions. [agent-tasks.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/fixtures/agent-tasks.mjs) is the single source for task descriptions, initial sources, and acceptance.

## Preparation and acceptance

```bash
node cli/compiler/scripts/agent-benchmark.mjs prepare add_feature .tmp/agent-run/add_feature
node cli/compiler/scripts/agent-benchmark.mjs verify add_feature .tmp/agent-run/add_feature launcher-argv.json .tmp/agent-run/add_feature-report.json
```

The destination directory must not exist. Preparation creates only TASK.md and the app project; acceptance sources are not put into the submission directory. An Agent edits the project according to TASK.md and may use [Semantic Query](/tooling/semantic-query), [Semantic Refactoring Preview](/tooling/rename-preview), and [Checks and Tests](/tooling/verification).

The launcher file contains a complete argv array for starting the CLI, such as `["C:/tools/norm.exe"]`. A development distribution uses the Java 25 executable, JVM arguments, module path, and main-module arguments; consult the generated launch script for exact values. The benchmark neither joins shell commands nor executes `.bat` directly. The launcher path must be absolute, and its version must support the current machine-output contract.

Acceptance creates a separate evidence directory next to the report, copies the submission, then injects acceptance. It preserves captured file digests, startup arguments, every CLI invocation, process exit codes, raw output, duration, and byte counts. The report file must not exist already; old evidence is not overwritten.

A test-addition task requires unchanged production sources, independently runnable tests associated with the target declaration, and submitted tests that separately reject incorrect implementations for negative, zero, and positive behavior. An API-change task verifies that old calls no longer compile; a rename task verifies that the old declaration was not retained behind a compatibility wrapper.

Outcomes are passed, failed, or invalid. Startup failure, a broken output protocol, or compiler infrastructure failure is invalid and cannot count toward a valid task failure rate. Script exit codes are 0, 1, and 2 respectively. See [agent-benchmark.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/agent-benchmark.mjs) and [agent-benchmark.test.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/agent-benchmark.test.mjs) for runner implementation and tests.

## Measurement boundary

Passing behavioral acceptance shows that the submission satisfies those cases; it does not establish Agent success rate or performance improvement. Reported `durationMs` is CLI-call duration, and output byte count is not model token count.

When comparing Agents, fix the tasks, model, prompt, and toolchain inputs. Create a separate submission directory per attempt and record model version, repair rounds, full elapsed time, and token usage from actual Agent sessions. Keep unavailable metrics missing rather than deriving them from byte counts; exclude invalid runs and retain evidence from failed attempts.

A maintainer's walkthrough, in which the same person writes acceptance and completes a task, confirms that the tooling and benchmark run. Report it separately from independent or blind model evaluation. Do not claim improvement over other tools or models without independent measurements.
