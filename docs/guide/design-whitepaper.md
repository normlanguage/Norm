# Norm language design white paper

## Abstract

Norm is a statically and strongly typed programming language for application development. It aims to retain the readable, practical engineering qualities of languages such as Java, Kotlin, and C#, while redefining several semantic boundaries that affect long-term maintainability: values and object identity, null, control-flow results, runtime generic types, framework metadata, system resources, and compilation artifact identity.

Norm does not aim to offer the largest number of language features. It seeks fewer concepts that compose reliably, with the compiler, editor, runtime, and official distribution all implementing the same program meaning.

## 1. The design problem

Complexity in large applications often comes from local code carrying too little information.

An ordinary assignment may copy structure, share an object, or refer to a mutable location. Function failure may use null, a status code, Result, an exception, or a framework wrapper. Generic types may disappear after compilation. An annotation may be passive metadata or change calls at runtime. Editors and build tools may even implement different name-resolution rules.

Documentation can explain each issue separately, but their combination continually increases the cost of understanding code. Norm aims to represent high-impact semantics in language types, declarations, and calls rather than leaving them to habits or framework conventions.

## 2. Language scope

Norm primarily serves backend services, business systems, desktop applications, command-line tools, and shared application libraries. It uses garbage collection and runtime support; ordinary application developers need not manage object lifetimes or prove borrowing relationships.

The language uses nominal static types. Type relationships require `extends` or `implements`; matching member shapes do not imply compatibility. Ordinary types are non-null, with `T?` explicitly allowing null. Locals and fields require definite assignment, while conversions and null narrowing follow static rules.

Extreme metaprogramming, kernel development, hard real-time execution, and type-level computation are outside Norm's core goals. This boundary lets the language prioritize engineering readability, diagnostics, and application runtimes.

## 3. Data model

### 3.1 Class

`class` represents identity-bearing objects. Classes may have mutable fields, methods, constructors, single inheritance, and interface conformance.

```norm
class Session {
    String state

    activate() {
        state = "active"
    }
}
```

A class variable holds an object reference. Assignment, parameter passing, and returns preserve the same object identity, and `==` compares identity. `copy()` creates a new top-level object; class-valued fields continue to share their nested identities.

### 3.2 Value

`value` represents structural data defined by its content.

```norm
value Money {
    Decimal amount
    String currency
}
```

Value fields cannot be reassigned after construction. Assignment and call boundaries produce logically independent values, while `==` and hashing recursively follow field semantics. The compiler and runtime may eliminate copying or share internal storage as long as programs cannot observe identity.

Primitive types, enums, and builtin containers also belong to the value model. Containers copy their own structure; class elements retain their object identities.

### 3.3 Ref

`ref<T>` refers to a value storage location. It does not accept classes or implement object sharing.

```norm
Integer cursor = 0
ref<Integer> location = &cursor
*location = 8
```

These three categories answer what the data is, which object it is, and where a value is stored. A single general reference model does not have to carry conflicting semantics.

See [value and identity semantics](/spec/value-identity-semantics) for the complete rules.

## 4. Functions and calls

Functions are top-level language constructs and need not be placed in classes. Packages organize declarations, classes represent objects, and functions express behavior independent of object state.

```norm
Integer clamp(Integer value, Integer minimum, Integer maximum) {
    if value < minimum {
        return minimum
    }
    if value > maximum {
        return maximum
    }
    return value
}

Integer opacity = clamp(value: input, minimum: 0, maximum: 100)
```

Calls with multiple arguments bind by name. Parameter names belong to the public calling convention, while argument expressions are still evaluated left to right in source order.

Class methods access object state and participate in dynamic dispatch. A method with an omitted return type is fluent and returns the same receiver. A method with no result explicitly declares `Void`.

Extension functions let explicitly imported top-level functions use dotted syntax:

```norm
extension String quoted(String value) {
    return "\"" + value + "\""
}

String text = "Norm".quoted()
```

An extension neither modifies the target type nor enters its dynamic method table. Real instance methods take precedence; extension candidates follow ordinary static overload rules.

## 5. Control flow and results

The Norm 1.0 specification allows `if`, `for`, and `switch` to produce values, with expression paths explicitly supplying results. The current release implements exhaustive switch expressions; version records define the remaining implementation boundaries.

```norm
String describe(Token token) {
    return switch token {
        case Name(String text) { break text }
        case End { break "end" }
    }
}
```

Norm does not implicitly use a block's last expression as its result or insert null for incomplete paths. A `for` expression uses `else` for normal exhaustion. A `switch` must be exhaustive and does not fall through.

Enum variants can carry data, and switch patterns destructure their payloads. `Result<T, E>` is an ordinary generic enum built from these capabilities, not special compiler control flow.

## 6. Generics and runtime types

Norm generics are invariant. Type positions specify complete arguments, while constructors and generic calls can infer them from expected types and arguments. Resolved type arguments enter canonical Core and the runtime type environment without type erasure.

Reified types support dynamic dispatch, reflection, annotations, serialization, and runtime diagnostics. Public reflection starts from type literals:

```norm
Class<Order> type = Order.class
List<Field<Order, ?>> fields = type.fields()
```

Field declaration references retain stable identity, owner, declared type, and runtime annotations. `Field<Owner, Value>.read(Owner)` returns the exact Value type directly, without searching for getters by string or relying on JVM reflection.

## 7. Annotations and controlled extension

An annotation is an identity aggregate in Norm's object model. Ordinary interfaces declare its allowed targets, metadata retention, and optional lifecycles.

Metadata-only annotations can implement target interfaces such as `TypeTarget` and `FieldTarget`, together with `RuntimeRetention`. To participate in execution, an annotation explicitly implements `FunctionInterceptor`, `ParameterInterceptor<T>`, or `FieldInterceptor<T>`.

This separates where an annotation may appear, how long its metadata is retained, and whether it executes behavior. Definition-side lifecycles enter the shared paths for ordinary calls, construction, dynamic dispatch, and function references; they do not rely on call-site proxies or runtime scanning.

Norm provides no macros, compile-time code-derivation DSL, or runtime code injection. Validation uses typed parameter and field lifecycles, while serialization uses passive metadata. They share an annotation model without sharing unnecessary execution mechanisms.

## 8. Absence, failure, and resources

Norm distinguishes three cases by the caller's responsibility:

- Nullable types represent ordinary absence.
- `Result<T, E>` represents expected outcome alternatives within a business contract.
- Exceptions represent system, protocol, and runtime conditions that prevent normal completion.

The language has no implicit Result propagation. Exceptions use `throw`, `try`, `catch`, and `finally`; system standard-library APIs throw domain exceptions with stable codes, operations, and reasons.

External resources use `Resource`, `ByteReader`, `ByteWriter`, and scoped `use` APIs. Reading all content requires a limit. HTTP response bodies and file streams use the same deterministic closure model. Execution contexts and platform adapters propagate cancellation and timeouts without a global service locator.

## 9. Standard library and application boundaries

Norm's public standard-library APIs are written in Norm. Backend-neutral system contracts connect host capabilities, with JDK adapters providing the current official platform implementation.

Core HTTP request bodies use `Bytes` without depending on JSON. JSON composition in `std.http` calls `std.serialization`, allowing protocol transport and data formats to evolve independently.

`DataMapper`, `DataReader<T>`, and `DataWriter<T>` define the common structural-serialization entry points. JSON, XML, and YAML share exact Core type shapes, field access, and canonical construction paths. Each format supplies its own tokens, format metadata, and error mapping. Automatic mapping covers explicitly marked values; class object graphs, cyclic references, and polymorphism require separate identity protocols.

Web servers, databases, and dependency injection belong to the application platform, not core language semantics. The [standard-library index](/stdlib/overview) and [version records](/versions/) define current availability.

## 10. Compilation and execution architecture

The official toolchain follows one semantic pipeline:

```text
Norm Source
    → Lexer / Parser
    → SemanticModel
    → Binder
    → Canonical Core IR
    → Truffle Backend
    → JVM execution / JIT
```

SemanticModel is the single result of name resolution, type checking, call selection, generic instantiation, visibility, and editor authoring information. The binder freezes verified semantics into resolved references; Core does not resolve source names again.

Canonical Core uses content-addressed definition identities. Public ABI, code, runtime metadata, debug information, and final executables each derive identity from their actual dependencies. This supports precise incremental invalidation, cross-process definition stores, and Truffle artifact reuse.

Truffle is the sole official execution backend. The CLI, Language Server, test entry points, Java bindings, and project loading share one JVM and project-system lifecycle. Official distributions carry a platform runtime rather than introducing another execution model.

See the [compiler architecture](/spec/compiler-design) and [implementation strategy](/design/implementation-strategy) for details.

## 11. Tools and distribution

The official VS Code extension handles editor integration. `norm lsp` and compiler semantic snapshots provide diagnostics, completion, signatures, formatting, navigation, and rename. The extension does not maintain separate language rules.

Tagged releases build standalone CLIs for Windows x64, Linux x64, and macOS ARM64, and package same-version CLIs for every supported platform in one universal VSIX. The release task generates checksums and build attestations only after each platform passes the same language acceptance, LSP smoke, and Extension Host tests.

## 12. Specification and implementation

Norm's language specification defines long-term 1.0 semantics. Version records define what the current release implements. A stable specification goal is not automatically a current product capability, and a release must not extend the language through undocumented implementation behavior.

This distinction lets language design establish a complete direction in advance while users make decisions based on executable, verified version contracts.

## Conclusion

Norm rests on mutually supporting boundaries: values carry no identity, class assignment preserves identity, and refs point only to value storage. Control flow explicitly supplies results; generic types remain available at runtime; annotations and extensions follow ordinary type and call rules; typed standard-library APIs connect system resources and data formats to one execution backend.

Together, these choices aim to keep application behavior understandable from source even as the codebase grows.

Next: [Comparisons, tradeoffs, and direction](/guide/comparison-and-future).
