<script setup>
import { currentRelease } from '../.vitepress/release'
</script>

# Project Status

The current release is Norm {{ currentRelease }}. This page describes delivered behavior; the specification describes the long-term language contract.

## Delivered

- classes, values, nominal interfaces, data enums, and exhaustive switch;
- nullable types, bidirectional generic inference, lambdas, declaration references, and extensions;
- lexically constrained `ref<T>` references;
- `Class<T>`, typed declaration metadata, annotations, interceptors, and structured `@Document` metadata;
- packages, modules, formatting, semantic diagnostics, completion, signatures, hover, navigation, references, and rename;
- Unicode text, collections, streaming I/O, files, an HTTP client, JSON, XML, YAML, validation, and testing APIs;
- self-contained CLIs with bundled Java runtimes and the official VS Code extension.

## Current limits

- string interpolation reports `NORM-LEXER-0005`;
- source comments are not implemented by the current lexer;
- automatic structural mapping supports values, not class identity, object graphs, cycles, or polymorphism;
- the standard library does not yet provide an HTTP server;
- a debugger and online execution environment are not part of the release.

See the [release index](/en/versions/) for the latest implementation contract, evidence, and version history.
