# Language Reference

This page indexes the core Norm language rules. The Language Tour explains how to use the language; the Reference specifies what the compiler must accept, reject, and execute.

Syntax not yet implemented in the current release is not presented as an available rule. [Status](/status) records version maturity and limitations in one place.

## Design boundaries

Norm is static, nominal, and non-null by default. Its core differences center on three concrete rules:

1. Classes preserve object identity, built-in containers retain value semantics, and explicit `copy()` creates a new top-level object.
2. As expressions, `if`, `for`, and `switch` produce results explicitly with `break value`.
3. Generic type arguments survive at runtime rather than being erased.

The language has no macros, operator overloading, implicit string conversion, implicit nullability, raw types, or implicit Result propagation.

## Source files and modules

Source uses UTF-8. A project file declares a package first, followed by imports and top-level declarations. A file without a package runs as a single-file script. Top-level types and functions are allowed; no static utility class is required.

```norm
package geometry

Integer coordinateSum(Point point) {
    return point.x + point.y
}
```

## Declarations

Types precede names:

```norm
String name = "Ada"
Integer age = 36

Integer square(Integer value) {
    return value * value
}
```

Core declarations include class, value, interface, enum, annotation, and function. Interfaces are the sole nominal behavioral abstraction; standard-library protocols are ordinary interfaces. Declarations are `public` by default, while `private` limits visibility to the declaring file. `module.norm` exports and direct dependencies jointly determine cross-package and cross-module visibility.

Omitting a top-level function's return type means `Void`. Omitting a class method's return type means it returns its receiver, with the full owner type in its actual signature. Explicit `Void` produces no result.

## Type system

`extends` and `implements` explicitly declare type relations; matching member shapes do not. Ordinary `T` excludes null, while `T?` includes it. The compiler checks definite assignment and narrows nullability through control flow.

Norm has no universal Object root type. Generic constraints and interfaces express general behavior.

## Value model

Classes are mutable and have identity; assignment, argument passing, and return share an object. Primitive types, enums, and built-in containers are values. `class.copy()` creates a new top-level object. Values compare structurally and classes by identity. See [value and identity semantics](/spec/value-identity-semantics) for the complete definition.

`ref<T>` refers to a value storage location; it is not a class-sharing mechanism. See the [`ref<T>` reference syntax](/spec/grammar/references) for its full boundary.

## Control flow

`if`, `for`, and `switch` may be statements or expressions. Expression paths must produce values explicitly:

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

A control expression does not take its final expression as an implicit result or insert null for a missing branch. Every switch must be exhaustive; its matched expression is evaluated once, and cases do not fall through. See [advanced function rules](/spec/grammar/functions-advanced) for the trailing-expression rule of lambdas. Iterator-style `for` works through the standard-library Iterable interface. Current syntax has no C-style `for` or `while`.

## Generics

Generics are invariant. Type positions must supply all required arguments; trailing arguments with declared default types may be omitted. In an expression, a diamond constructor may solve arguments from an expected type and constructor arguments. The full result from defaults and inference enters Core IR and the runtime type environment.

## Errors

Use nullable types for ordinary absence and `std.core.Result<T, E = String>` for expected failure. Omit `E` when the reason is text only; supply it for typed classification. Success without a business value uses `std.core.Unit`. Result is an ordinary generic enum; the language does not propagate it automatically. Exceptions use `throw`/`try`/`catch`/`finally`. Resource cleanup must be visible on every completion path or guaranteed by a standard-library scoped API.

## Evaluation

Subexpressions and arguments evaluate from left to right in source order. Named arguments change parameter binding, not evaluation order. Logical operators short-circuit. An optimizer may eliminate value copies or share internal storage, but cannot change object identity, I/O order, or dynamic type.

## Reference navigation

- [Grammar overview](/spec/grammar/overview)
- [Type system](/spec/type-system)
- [Value and identity semantics](/spec/value-identity-semantics)
- [Packages and modules](/spec/module-system)
- [Reference lifetime](/spec/grammar/references)
- [Annotation semantics](/spec/annotations)
- [Declaration references and reflection](/spec/declaration-references)
- [Current limitations](/status)
- [Object model](/spec/object-model)
- [Memory semantics](/spec/memory-semantics)
- [Expression semantics](/spec/expression-semantics-formal)
- [Formal semantics](/spec/formal/semantics)
- [Compiler design](/spec/compiler-design)
