# Enum design

A Norm enum is a closed algebraic data type. Each variant may carry no data or have its own field set.

```norm
enum Token {
    Number(Double value),
    Name(String text),
    End
}
```

## Construction and type

`Token.Number(value: 1.5)` has static type `Token`, not a public subtype. A variant constructor initializes only its declared data; variants cannot be inherited or implemented separately. Explicit construction of a generic enum writes all type arguments on the type name, for example `Result<Integer, Error>.Ok(value: 1)`. Omitting type arguments uses ordinary call-inference rules.

## Matching

```norm
String describe(Token token) {
    return switch token {
        case Number(Double value) { break "number" }
        case Name(String text) { break text }
        case End { break "end" }
    }
}
```

The compiler recursively analyzes pattern coverage, so every switch must be exhaustive. Pattern bindings have static types and are scoped to their case block.

## Evolution rules

Adding a public enum variant within the same compatibility level is a source-breaking change: existing exhaustive switches will need another case. Library authors who need an open set of extensions should use an interface instead of reserving an `Unknown` variant to hide model changes.

Library types such as `Result<T, E>` use ordinary enum definitions. They gain neither hidden control flow nor a special runtime representation.
