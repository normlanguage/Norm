---
title: Language Tour
description: Build a complete mental model of Norm in twelve chapters
---

# Language Tour

Learn Norm's core language model in about fifteen minutes while running programs continuously checked against the current compiler.

Norm uses different constructs for different semantics: `class` for identity, `value` for data, `enum` for finite alternatives, `interface` for capability, and `ref` for controlled aliasing. The tour follows that model rather than listing parser or AST features.

## Reading order

| Chapter | What you will learn |
| --- | --- |
| [01 Hello, Norm](/learn/hello) | Source files, the entry point, and basic code structure |
| [02 Values and bindings](/learn/bindings) | Explicit types, `var`, assignment, and literals |
| [03 Functions and calls](/learn/functions) | Return types, argument labels, and evaluation order |
| [04 Class, Value, and Interface](/learn/data-model) | Identity, values, and nominal capabilities |
| [05 Data Enum and Switch](/learn/enum-switch) | Finite states, destructuring, and exhaustive branches |
| [06 Null and type inference](/learn/nullability-inference) | Nullability, expected types, and inference limits |
| [07 Collections and iteration](/learn/collections) | Array, List, Iterable, and indexes |
| [08 Lambda and Extension](/learn/lambdas-extensions) | Function values, capture, and statically resolved extensions |
| [09 Errors and exceptions](/learn/errors) | Expected results and exceptional control flow |
| [10 References](/learn/references) | Addressable locations and lexical lifetimes |
| [11 Annotation](/learn/annotations) | Typed metadata and interception behavior |
| [12 Package and Module](/learn/packages-modules) | Multi-file programs and public boundaries |

## Tour, Reference, and Status

The tour supports continuous learning and omits uncommon edge cases. The [Language Reference](/spec/language-spec) precisely defines what the compiler should accept, reject, and execute. [Status](/status) describes only the capabilities and limitations delivered in the current release.

Start with [Chapter 1: Hello, Norm](/learn/hello).
