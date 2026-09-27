# Design principles

The language philosophy describes what Norm values. This page defines how a feature qualifies for the language, standard library, or official toolchain. These are engineering principles for evolution, not a list of syntax features.

## 1. Define one rule for observable behavior

The same syntax should mean the same thing in different contexts. Frameworks and backends must not redefine name resolution, evaluation order, copying, equality, nullability, or failure propagation for their own needs.

When a capability needs special behavior, first determine whether it belongs to the language, standard library, or application platform, then establish one contract at that layer. Optimizations and host adapters implement that contract; they must not become independent sources of semantics.

## 2. Represent different data relationships with different types

Structural data, object identity, and storage locations are different relationships, represented by `value`, `class`, and `ref<T>`. Ordinary absence, business outcomes, and system exceptions are also expressed separately.

If an abstraction makes callers guess from documentation whether an object is shared or an error is thrown, its type boundary is incomplete.

## 3. Keep information where readers need it

Declarations establish long-term contracts. Call sites retain the information needed to understand the current operation.

- Ordinary types are non-null; write `?` when null is permitted.
- Multiple arguments retain labels; parameter names belong to the public calling convention.
- Control-flow expressions use `break` where they produce a result.
- Extensions require explicit declaration and import.
- Reflection starts from reified type parameters rather than type-name strings.

Explicit information should be stable and distinctive. Repeated types, meaningless wrappers, and reliably inferable local details do not serve this principle.

## 4. Advanced capabilities follow ordinary rules

New capabilities must not bypass name resolution, type checking, visibility, or evaluation order.

Extension functions reuse top-level functions and overload resolution. Annotations are ordinary aggregates whose nominal policy interfaces provide targets, retention, and lifecycles. Structural serialization reads Core types and field metadata. Module descriptors are themselves programs compiled and evaluated by Norm.

If a capability requires a macro DSL, classpath scanning, string-based method names, or implicit global registration, reconsider the underlying language boundary first.

## 5. Defaults serve long-lived application code

Norm prioritizes backend services, business systems, tools, and desktop applications rather than kernels, hard real-time systems, or extreme type-level programming.

The language therefore chooses garbage collection, non-null defaults, nominal types, and definite assignment. The standard library chooses bounded reads, deterministic resource closure, and typed domain exceptions. The toolchain chooses self-contained CLIs and consistent editor semantics.

Defaults should make ordinary code safe and clear; explicit APIs handle less common requirements.

## 6. Separate language, standard library, platform, and toolchain

| Layer | Responsibility |
| --- | --- |
| Language | Types, values, calls, control flow, and evaluation semantics |
| Standard library | General APIs for collections, I/O, time, files, HTTP, serialization, and related capabilities |
| Application platform | Web servers, databases, configuration, dependency injection, and deployment models |
| Toolchain | Compilation, Core, LSP, testing, packaging, and release |

Lower layers should not know higher-level frameworks. Core HTTP bodies use `Bytes`, with JSON composition exposed separately. Reflection provides structural capabilities, while serialization determines mapping rules. This layering supports composition without embedding a particular format or framework into the language.

## 7. Define each fact once

Do not maintain a second handwritten copy of information that can be generated or derived.

Declarative schemas generate the builtin ABI. The compiler and LSP read the same `SemanticModel`. JSON, XML, and YAML share serialization shapes. One configuration supplies the website base, manifest, and public URLs.

Documentation explains concepts, boundaries, and navigation. Where code already defines an implementation detail, link to that source or API instead of copying logic that can drift.

## 8. Optimize beneath the semantic boundary

Core canonicalization, definition stores, Truffle specialization, structural sharing, and runtime packaging may change program representation and execution, but must preserve observable behavior.

Backends consume resolved canonical Core and must not repeat language-level overload resolution or type inference. Cache invalidation follows typed identities and actual dependencies, rather than using file timestamps or string keys as substitutes for semantic identity.

## Questions before accepting a feature

A proposed capability should answer at least these questions:

1. Does it solve a language problem, provide a general system capability, or address an application-framework concern?
2. Can callers understand sharing, failure, and lifecycles from types and source?
3. Does it reuse existing name-resolution, type, call, and resource protocols?
4. Would it create a new source of truth for metadata, configuration, or execution?
5. Do the compiler, LSP, tests, and official distribution observe the same behavior?
6. After removing the old path, does the overall number of concepts remain manageable?

When these questions cannot be answered clearly, adding more APIs usually postpones a structural problem.

Next: [Language design white paper](/guide/design-whitepaper).
