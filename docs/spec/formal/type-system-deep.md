# Detailed type-system rules

This page clarifies boundaries where combinations of nullability, generics, functions, and dynamic types can be ambiguous.

## Nullable combinations

The nullable marker applies to a complete type: `List<String>?` differs from `List<String?>`. Repeated nullability, `T??`, does not create a new type; it should normalize to `T?` or produce a redundancy diagnostic.

## Reified generics

`List<String>` and `List<Integer>` retain different arguments in Core IR and the runtime type environment. They do not rely on an external token after erasure.

## Function types

```norm
Function<String(Point)> formatter
```

A function type is determined by its return type and ordered parameter types, without parameter names. A lambda uses its expected type and its own constraints for bidirectional inference. It may capture effectively final outer locals, parameters, and `this`; a bound method reference explicitly carries its receiver.

## Dynamic dispatch and copying

A superclass or interface variable preserves the full dynamic type. After copying a class value, both copies retain that dynamic type but do not share mutable fields. Public virtual behavior dispatches on dynamic type.

## Cast

`is` checks only declared relations and reified generic information. `as` is an explicit operation that may fail. The public spelling of a safe cast depends on the final syntax proposal; examples must not assume `as?` before it is finalized.

## Bottom and Never

Throw and non-returning functions do not complete normally in control flow. An implementation may use an internal bottom/Never type when joining branches, but whether that becomes a declarable public type is not yet finalized.
