# Declaration Syntax

Declarations create module members, type members, or local bindings. Norm puts types first so an API's shape appears before its name.

## Top-level forms

```text
Declaration := Visibility? (
    ClassDeclaration
  | ValueDeclaration
  | InterfaceDeclaration
  | EnumDeclaration
  | AnnotationDeclaration
  | FunctionDeclaration
)
```

## Class and Value

```norm
class Counter {
    Integer value
    Void increment() { value = value + 1 }
}

value Point {
    Integer x = 0
    Integer y = 0
}
```

A class may contain mutable fields and behavior, and assignment retains object identity. A value is immutable after construction and follows value assignment rules. A field default makes its implicit constructor parameter optional and evaluates at construction in field order. Required fields precede defaulted fields. Fields without defaults still obey definite-assignment requirements.

## Interface

```norm
interface Formatter<T> {
    String format(T value)
}
```

An interface declares behavior only. A satisfying relationship must explicitly write `implements`.

## Enum

```norm
enum State {
    Active,
    Disabled(String reason)
}
```

A variant's parameters fully declare its carried data. An enum is closed and can be exhaustively matched by switch.

## Functions

```norm
Integer coordinateSum(Point point) {
    return point.x + point.y
}
```

The return and parameter types after applying omission rules, plus public parameter names, are part of the signature. A return-type-only change cannot form an overload.

## Annotation

An annotation is a special class with target and retention-policy interfaces. It can declare fields, constructors, and methods. See the [annotation specification](/spec/annotations) for complete semantics.

## Duplicates and scope

Conflicting names cannot be declared in one scope. A local is visible after its declaration through the end of its block. A type parameter is visible only in its owning declaration and that declaration's member signatures and implementations.
