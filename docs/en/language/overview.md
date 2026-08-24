# The Norm Language Handbook

This handbook introduces the syntax that most clearly distinguishes Norm from other statically typed application languages. It is a learning guide, not a complete specification.

## A first program

```norm
Integer square(Integer value) {
    return value * value
}

Void main() {
    Integer result = square(4)
    printLine(result)
}
```

Norm uses type-first declarations, braces, optional semicolons, and named arguments for multi-parameter calls. Labels use `name: value`; a single argument may omit its label, and a matching identifier is shorthand for the corresponding label. Functions can be declared at package level; there is no `static` keyword.

## The three defining features

### Value and identity semantics

```norm
Counter second = first
Counter copied = first.copy()
```

The first declaration preserves object identity. The second creates a new top-level object identity.

### Explicit values from control flow

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

Norm does not use the final expression of a block as its implicit result. `break value` identifies where the result is produced.

### Contextual generic inference

```norm
List<String> names = List<>()
```

Expected types and expression types jointly determine omitted generic arguments.

## Reading order

1. [Value and identity](/en/language/objects)
2. [Control-flow expressions](/en/language/control-flow)
3. [Reified generics](/en/language/generics)

The complete Chinese handbook also covers basic syntax, nullability, functions, interfaces, enums, errors, and reflection.
