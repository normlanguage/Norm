---
title: Status
description: Norm's delivered capabilities, maturity, and implementation boundaries
---

<script setup>
import { currentRelease } from './.vitepress/release'
</script>

# Status

The current formal release is Norm {{ currentRelease }}. This page describes the current toolchain. See the [Language Reference](/spec/language-spec) for long-term language rules and the [version index](/versions/) for per-release delivery records.

## Maturity labels

| Label | Meaning |
| --- | --- |
| Stable | Part of the current release contract, with automated acceptance |
| Experimental | Implemented, but its public shape may still change |
| Internal | Used inside the toolchain without a promised public entry point |
| Planned | A documented design or direction that cannot currently be used |

## Language

| Capability | Status | Source of truth |
| --- | --- | --- |
| Class, Value, Interface | Stable | [Object model](/spec/object-model) |
| Data enums and exhaustive switch | Stable | [Enum and switch](/spec/grammar/switch) |
| Nullable types, `?.`, `??`, and control-flow narrowing | Stable | [Type system](/spec/type-system) |
| Generic types, functions, methods, and bidirectional inference | Stable | [Type inference](/spec/type-inference) |
| Lambdas, function values, and declaration references | Stable | [Advanced function rules](/spec/grammar/functions-advanced) |
| Extension functions | Stable | [Function reference](/spec/grammar/functions#extension-functions) |
| `ref<T>` and lexical lifetimes | Stable | [Reference rules](/spec/grammar/references) |
| `Class<T>` and typed declaration references | Stable | [Declaration references and reflection](/spec/declaration-references) |
| Annotations, `@Document`, and typed interceptors | Stable | [Annotation specification](/spec/annotations) |
| Packages, modules, and cross-file visibility | Stable | [Module system](/spec/module-system) |
| Typed string interpolation | Stable | [Literals](/spec/grammar/literals) |
| `for` with `break value` and `else` in a value position | Planned | [Loop design](/spec/grammar/loops#for-expressions); the current parser rejects `Integer result = for ...` |
| `//` and `/* */` source comments | Planned | The current lexer treats these markers as operator tokens |

## Standard library

| Area | Status |
| --- | --- |
| Core types, collections, Unicode text, Math, Time | Stable |
| Streaming I/O, filesystem, and resource lifetimes | Stable |
| HTTP client | Stable |
| Command-line parsing, application runtime environment, standard streams, and subprocesses | Experimental |
| JSON, XML, YAML, and unified structured mapping | Stable |
| Validation and Testing | Stable |
| Automatic mapping of values | Stable |
| Automatic mapping of class identity, object graphs, cycles, and polymorphism | Planned |
| HTTP server | Planned |

See the [standard library overview](/stdlib/overview) for delivered modules. The source and acceptance programs define public entry points.

## Tooling

| Capability | Status |
| --- | --- |
| CLI with bundled Java runtime and JVM development entry | Stable |
| Formatting, diagnostics, completion, signature help, and hover | Stable |
| Go to definition, find references, prepare rename, and rename | Stable |
| Read-only navigation of standard library source | Stable |
| Official VS Code VSIX | Stable |
| Debugger | Planned |
| Online playground | Planned |

## Known boundaries

- Automatic serialization currently handles `value` only.
- `private` has a source-file boundary.
- Generic parameters remain invariant; raw types are unsupported.
- References cannot be stored in fields, containers, generic arguments, return types, or lambda captures.
- There is currently no HTTP server, debugger, or online execution environment.

Adoption decisions should use the [latest implementation contract](/versions/) and actual acceptance programs.
