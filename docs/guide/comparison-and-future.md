# Comparisons, tradeoffs, and direction

Norm does not combine every feature from existing languages. It chooses a familiar statically typed application model, then places different constraints on values and identity, control-flow results, runtime generics, and framework extensions.

The most useful language comparison asks who carries the complexity of an engineering problem, rather than counting keywords.

## Key dimensions

| Dimension | Norm | Java | Kotlin | Rust | Go | TypeScript |
| --- | --- | --- | --- | --- | --- | --- |
| Null | Non-null by default; `T?` permits null explicitly | Ordinary references may be null, with annotations providing additional checks | Non-null by default, with platform types at boundaries | `Option<T>` | Pointers, interfaces, and some other types may be nil | Unions under `strictNullChecks` |
| Data model | Distinct roles for `value`, `class`, and `ref<T>` | Separate class, record, and primitive rules | Class, data class, and value class | Move, copy, references, and ownership | Values, pointers, and interfaces | Object references and structural types |
| Runtime generics | Reified; exact arguments enter Core and runtime | Type erasure | Ordinary generics are erased; inline reification is a local capability | Monomorphization | Compile-time instantiation | Fully erased at runtime |
| Control-flow results | Explicit `break value` | Most control flow consists of statements | A block's last expression supplies its value | A block's last expression supplies its value | Control flow consists of statements | Conditional expressions and statements are distinct |
| Extension mechanisms | Explicitly imported static extensions and typed annotation protocols | Annotations, reflection, processors, and agents | Extensions, annotations, and compiler plugins | Traits and macros | Interfaces and code generation | Decorators, declaration merging, and transformation tools |
| Failure model | Separate nullable, Result, and Exception roles | Optional, return values, and exceptions | Nullable types, Result patterns, and exceptions | Option, Result, and panic | Multiple-return-value errors and panic | Unions, Promise rejections, and exceptions |
| Deployment | Bundled reduced Java runtime | JVM/JAR or native tools | JVM and native backends | Native binaries | Native binaries | JavaScript runtime or bundle |

The table describes default models, not what other languages can achieve through libraries or conventions. Norm fixes these boundaries jointly in its language and official toolchain.

## Java: familiar engineering structure, different runtime types

Java developers will recognize Norm's type-first declarations, packages, classes, interfaces, exceptions, annotations, and nominal subtyping. Norm also retains class reference semantics rather than interpreting every class assignment as a deep copy.

The main changes are non-null ordinary types, top-level functions without utility classes, `value` for structural data, retained generic arguments, and named parameters as part of the public calling convention. Annotation lifecycles and structural reflection read Norm Core metadata rather than using Java reflection as the language model.

Norm does not yet have Java's library ecosystem, mature build ecosystem, or long production history. JDK platform adapters and established third-party libraries reuse host capabilities, but that does not by itself make source-level Java interoperability a stable public language feature.

## Kotlin: fewer syntax alternatives, broader shared rules

Norm and Kotlin both value non-null defaults, top-level functions, data modeling, and concise calls. Their basic view of extensions is similar: dotted syntax may come from a static function without modifying a class.

Norm places greater emphasis on reducing alternative spellings for the same responsibility. Class and value identity rules, `break value`, fluent class methods with omitted return types, and annotation policy interfaces use fewer, more restrictive rules. Norm has no `lateinit`, implicit-receiver DSLs, operator overloading, or compiler-plugin language extensions.

The tradeoff is a narrower space of expression. Kotlin projects that rely on DSLs, coroutine syntax, and a rich standard library cannot currently obtain an equivalent ecosystem from Norm.

## Rust: explicit type boundaries without ownership proofs

Norm draws on enum payloads, exhaustive switches, Result, and explicit resource scopes, but does not aim to replace Rust's systems-programming capabilities.

Rust uses ownership, borrowing, and lifetimes to prove memory and aliasing safety at compile time. Norm uses garbage collection and class/value/ref semantics to help application developers understand sharing. Norm's `ref<T>` is a controlled reference to value storage, not a general borrowing system.

This reduces the type-system burden on ordinary application code while giving up Rust's guarantees around GC-free execution, predictable resources, and low-level control.

## Go: simple deployment with richer type semantics

Norm and Go both aim for straightforward toolchains, simple distributions, and practical application boundaries. Norm's platform packages bundle their runtime, so users do not need to install a language runtime first.

Norm chooses nominal interfaces, non-null types, enum payloads, exceptions, runtime generic information, and class identity. Go chooses a smaller language surface, structural interfaces, explicit error returns, and more mature concurrency and networking libraries.

Go currently offers a substantially more complete server ecosystem and lightweight concurrency model. Norm offers a different tradeoff when object identity, structural values, and runtime types need to form one static model.

## TypeScript: runtime guarantees owned by the language implementation

TypeScript excels at gradual adoption, structural typing, type composition, and integration with the JavaScript ecosystem. Its types disappear at runtime, where JavaScript's object model determines behavior.

Norm uses nominal types and reified generics. The compiler produces canonical Core, and runtime reflection and serialization use the same exact types. It does not provide arbitrary structural types, complex conditional types, or implicit JavaScript coercion.

This strengthens consistency between static and runtime models but prevents direct use of the browser and npm ecosystems.

## Norm's current strengths

- Values, classes, and refs provide one checkable explanation of sharing.
- Named parameters and explicit control-flow results improve readability at calls and branches.
- Reified generics, Core metadata, reflection, and serialization form a complete type-information chain.
- Annotation metadata and typed interceptors share the object model without requiring macros.
- The compiler, LSP, and Truffle share one semantic and execution pipeline.
- System APIs use bounded streams, resource scopes, and domain exceptions, with support for files, HTTP, and structured data formats.

## Norm's current limitations

- The language, standard library, and toolchain are pre-1.0; compatibility promises and diagnostic contracts are not frozen.
- Libraries, package management, databases, web servers, concurrency, debugging, and profiling remain incomplete.
- Automatic structural serialization covers explicitly marked values, not class object graphs, cyclic references, or polymorphism.
- A self-contained runtime increases download size while retaining dynamic loading of independent Java packages and frameworks.
- Production performance, long-running behavior, and incremental work on large projects need more validation in real applications.

These limitations are part of deciding whether Norm fits a project, rather than documentation footnotes.

## Direction

The [version index](/versions/) records released capabilities; the [project roadmap](/design/roadmap) owns future work. This guide does not duplicate a milestone list that could drift from plans and actual delivery.

Norm continues to prioritize completing the foundations of application development before expanding the ecosystem, establishing shared abstractions before adding formats or platforms, and requiring new capabilities to enter the compiler, LSP, official release acceptance, and version contracts together.

Next: [Start the Language Tour](/learn/) or review the [current status](/status).
