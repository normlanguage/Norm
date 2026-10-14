---
title: Tooling
description: Norm CLI, language service, and editor integration
---

# Tooling

Norm's formatter, diagnostics, and editor features read the same semantic snapshot as the compiler. Type checking, name resolution, and project boundaries are implemented once.

## Current capabilities

- Formatting and compiler diagnostics;
- Completion driven by scope and expected type;
- Signature Help and Hover;
- Go to Definition and Find References;
- Prepare Rename and semantic Rename;
- Navigation across files, packages, and standard-library sources;
- Project analysis including unsaved documents.

Structured API documentation reuses the same semantic entry point; see [API Documentation Export](/tooling/api-documentation). See [Application Builds](/tooling/application-build) for self-contained application output conventions.

## Start with examples

With [Norm 0.25](/versions/0.25), run `norm hello` in an empty directory to create five independent `.norm` programs and English and Chinese guides. Start with `norm hell.norm`, then follow the generated [example guide](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/resources/hello/README.md).

`norm main.norm` hides startup preparation logs by default. Use `norm main.norm --debug` or `norm run --debug main.norm` to write startup phases, elapsed time, and actual Maven downloads to stderr. Error diagnostics always appear; this option does not affect application output. See [RunCommand](../../cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/RunCommand.java) for progress and [JarResolverTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JarResolverTest.java) for the network boundary of local dependencies.

## Update running code on save

`norm run main.norm` and `norm main.norm` watch project inputs during JVM source execution. Saving code selects the new version at execution boundaries while retaining running state. Monitoring ends when the program exits. Prepared application caches participate in the same execution path.

Failed compilation leaves the running version active. Ambiguous active regions retain their version and report a pending update. Destructive state changes require migration; no explicit state converter API is available. Built application artifacts load their prepared program directly. Annotation references to global functions with identical canonical content are rejected when the authored target is ambiguous; see [AnnotationRuntime](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/AnnotationRuntime.java).

The public update and batch lifecycle is defined by [ApplicationSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationSession.java) and [ApplicationUpdateResult](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationUpdateResult.java); state compatibility is defined by [CoreRuntimeSchema](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreRuntimeSchema.java). Validation entry points are [LiveExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveExecutionTest.java), [LiveStructureExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveStructureExecutionTest.java), [LiveMigrationBoundaryTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveMigrationBoundaryTest.java), [LiveRuntimeTypesExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveRuntimeTypesExecutionTest.java), and [SelfUpdatingApplicationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/SelfUpdatingApplicationTest.java).

Runtime identity and Java dependency components are indexed by [CoreRuntimeIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreRuntimeIdentity.java), [JavaRealmPool](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaRealmPool.java), and [ApplicationRuntimeRevisionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/ApplicationRuntimeRevisionTest.java). Transaction validation is indexed by [LiveContainerMigrationExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveContainerMigrationExecutionTest.java) and [LiveConstructionExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveConstructionExecutionTest.java). Transactions cover Norm state; external I/O is not rolled back.

## VS Code

For AI Agent navigation through specifications, queries, and validation, see [Agent Development](/tooling/agent).

The official VSIX bundles a same-version self-contained CLI for supported platforms. See [VS Code Development Experience](/guide/vscode) for installation, execution, project detection, and CLI selection; see the [release process](/design/release-process) for publication assets and platform matrix.

## Shared semantic entry point

```text
Source files
  → Project source set
  → Semantic model
  → diagnostics / completion / signatures
  → hover / navigation / references / rename
  → formatter / compiler
```

See [Status](/status) for the currently delivered tooling boundary. A debugger and online execution environment are not yet in a release.
