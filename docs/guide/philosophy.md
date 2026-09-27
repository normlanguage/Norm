# Language philosophy

Norm starts from the view that the most expensive part of a program's life is not writing it for the first time, but having different people repeatedly read, change, and run it. Language design should first reduce the long-term cost of understanding code.

This does not require verbose code everywhere. Norm removes wrappers that carry no semantic value, but keeps information that changes control flow, sharing, failure behavior, or runtime behavior visible.

## Visible semantics

A reader should be able to answer these questions locally:

- Can this value be null?
- Do both variables observe the same object after assignment?
- Which path produces the result of this control structure?
- Does failure represent ordinary absence, a business outcome, or a system exception?
- Does a dotted call invoke a real method or a static extension?
- Is an annotation only metadata, or does it execute lifecycle behavior?
- Which exact type is inspected at runtime?

Norm gives these distinctions stable source representations.

| Syntax | Visible meaning |
| --- | --- |
| `T?` | Null is permitted at this position |
| `value` / `class` / `ref<T>` | Structural value, object identity, or value storage location |
| `name: expression` | The public parameter bound to this argument |
| `break expression` | The control-flow construct produces its result here |
| `Result<T, E>` | Business outcome alternatives that the caller must handle |
| `throw` / `catch` | An abnormal execution path crosses call boundaries |
| `extension` | Dotted syntax invokes a static top-level function |
| `T.class` | The code obtains a `Class<T>` bound to that type |

## Remove wrappers, retain meaning

Behavior that does not depend on object state can be a top-level function:

```norm
Double midpoint(Double left, Double right) {
    return (left + right) / 2.0
}
```

No `static`, singleton object, or utility class is needed. Removing those structures loses no information.

By contrast, calls with multiple arguments retain parameter labels by default:

```norm
Double center = midpoint(left: start, right: end)
```

Labels add characters but keep the roles of two same-typed arguments at the call site. Norm measures concision by how much context readers must recover elsewhere, rather than by character count.

## Identity is distinct from value

Stateful objects need stable identity; structural data is understood through its content. Forcing both into one object semantics makes copying, equality, and sharing surprising across APIs.

Norm distinguishes:

- `class`: mutable and identity-bearing; ordinary assignment preserves the same object.
- `value`: defined by field content, immutable after construction, and structurally equal.
- Builtin containers: copy container structure while continuing to share the identities of class elements.
- `ref<T>`: refers to a value storage location and does not accept class types.

An implementation may optimize values with structural sharing, copy-on-write, and escape analysis, provided observable semantics remain unchanged.

## Control flow makes results explicit

Norm control-flow expressions mark their result with `break value`; the last item in a block does not become an implicit return. The current release implements exhaustive switch expressions. Value-producing forms of if and for remain goals of the 1.0 specification.

```norm
String describe(State state) {
    return switch state {
        case Ready { break "ready" }
        case Disabled { break "disabled" }
    }
}
```

A missing variant produces an exhaustiveness error rather than an implicit null. Expression support preserves explicit value flow.

## Strong types constrain boundaries

Norm uses non-null defaults, definite assignment, nominal interfaces, invariant generics, and reified type parameters. These rules prioritize protecting module boundaries rather than encouraging type-level computation.

The same reasoning applies to reflection and framework extensions. `Field<Owner, Value>` retains the owner and field value types. Annotation lifecycles constrain inputs through `ParameterInterceptor<T>` and `FieldInterceptor<T>`. Serialization builds plans from exact Core types. Runtime capabilities should preserve existing type information instead of reducing it to strings and untyped maps.

## Different failures carry different responsibilities

Ordinary absence, business rejection, and system failure require different control decisions from callers. Norm represents them separately with nullable types, Result, and Exception. It has no automatic Result propagation syntax and does not require system APIs to wrap exceptions in Result.

Function signatures can therefore express stable business alternatives while file, network, protocol, and runtime failures propagate through exception boundaries and are converted at an appropriate application layer.

## Extensions need boundaries

Norm introduces neither macros nor runtime code injection. Extension functions are explicitly imported static functions. Ordinary interfaces express annotation targets, retention policies, and interception protocols. Reflection reads Core metadata retained by the compiler.

Frameworks can compose validation, serialization, and lifecycle behavior within these boundaries, but cannot create an invisible program that bypasses language name resolution, type checking, and call rules.

## One semantics serves every tool

The compiler, formatter, Language Server, Core builder, and Truffle backend do not independently guess program meaning. Name resolution, call binding, generic arguments, field ordinals, and annotation applications enter one semantic pipeline; downstream tools consume its results.

This principle also requires official distributions to package the same Truffle implementation and JVM execution model. Development execution, editor analysis, and the final distribution must use the same language.

Next: [Design principles](/guide/design-principles).
