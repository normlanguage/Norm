---
title: Language
description: Norm's purpose, design principles, and engineering tradeoffs
---

# Language

Norm is a statically and strongly typed language for application development. It retains familiar structures such as type-first declarations, braces, classes, interfaces, and exceptions, while making the semantics of sharing, value flow, and runtime behavior visible in declarations and calls.

## Core design

```text
class       identity
value       data
enum        alternatives
interface   capability
ref         controlled aliasing
```

These five constructs are not syntactic aliases for one object model. They define distinct boundaries for identity, mutability, finite states, nominal capabilities, and location references. Functions, generics, annotations, the standard library, and the toolchain compose within those boundaries.

## Where to start

| Purpose | Documentation |
| --- | --- |
| Learn the language step by step | [Language Tour](/learn/) |
| Understand the design values | [Language philosophy](/guide/philosophy) |
| Evaluate whether a feature fits the language | [Design principles](/guide/design-principles) |
| Understand the language and runtime as a whole | [Language design white paper](/guide/design-whitepaper) |
| Compare specific dimensions with other languages | [Comparisons, tradeoffs, and direction](/guide/comparison-and-future) |
| Look up precise compiler rules | [Language Reference](/spec/language-spec) |
| Check implementation maturity | [Status](/status) |

This guide explains stable design intent without repeating tutorial steps or specification clauses. When the current release differs from the long-term specification, use [Status](/status) and the corresponding version contract to determine availability.

Next: [Language philosophy](/guide/philosophy).
