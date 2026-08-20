# Norm Expression Specification

## Expression Philosophy

Norm treats important control structures as expressions, but requires explicit value production.

There is no implicit final-expression return.

## Literals

Supported literals include:

- number
- string
- boolean
- null
- collection values

## Function Call

```norm
User user = User(
    name = "Alice"
)
```

Calls use named arguments by default.

## Assignment

Assignment changes the variable binding.

```norm
name = "Bob"
```

## If Expression

```norm
String state = if active {
    break "active"
} else {
    break "inactive"
}
```

All paths must produce a value.

## Switch Expression

```norm
String result = switch value {
    case Active {
        break "active"
    }
}
```

Exhaustiveness is checked where possible.

## No User Operator Overloading

Operators keep fixed language semantics.
