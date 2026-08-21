# The Norm Language Handbook

This handbook introduces the syntax that most clearly distinguishes Norm from other statically typed application languages. It is a learning guide, not a complete specification.

## A first program

```norm
int square(int value) {
    return value * value
}

void main() {
    int result = square(value = 4)
    print("result = ${result}")
}
```

Norm uses type-first declarations, braces, optional semicolons, and named arguments for multi-parameter calls. Functions can be declared at module level; there is no `static` keyword.

## The three defining features

### Value semantics and explicit sharing

```norm
Counter second = first
Ref<Counter> shared = first.ref()
```

The first declaration creates an independent value. The second explicitly introduces shared identity.

### Explicit values from control flow

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

Norm does not use the final expression of a block as its implicit result. `break value` identifies where the result is produced.

### Generic arguments at runtime

```norm
List<String>.class.T == String.class
```

Parameterized types retain their actual generic arguments at runtime.

## Reading order

1. [Value semantics and Ref](/en/language/objects)
2. [Control-flow expressions](/en/language/control-flow)
3. [Reified generics](/en/language/generics)

The complete Chinese handbook also covers basic syntax, nullability, functions, interfaces, enums, errors, and reflection.

