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

Run `norm run --watch main.norm` on the JVM to watch project inputs. Saving a compatible implementation change selects the new code at subsequent execution boundaries while retaining active frames, local values, and iterators. The entry point is not replayed. Watching stops when the program exits.

Compilation errors and incompatible changes produce diagnostics while the previous version keeps running; saving a correction retries the update. Callable contracts, local bindings and types, statement and expression structure, and runtime object layouts must remain compatible. Types and runtime metadata still use exact content identities, so changes such as editing class method implementations or annotated functions can also be rejected when those identities change. Java dependency, generated class, and resource changes require a restart. The complete compatibility checks live in [CoreLiveSchema](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreLiveSchema.java) and [LiveApplicationSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/LiveApplicationSession.java); see [LiveExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/LiveExecutionTest.java) for state preservation within one execution. This capability applies to source execution, not Native Image application artifacts.

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
