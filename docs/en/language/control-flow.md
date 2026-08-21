# Control-Flow Expressions

`if`, `for`, and `switch` can be used as statements or as value-producing expressions. Expression form always makes value production explicit.

## If expressions

```norm
String grade = if score >= 90 {
    break "A"
} else if score >= 60 {
    break "B"
} else {
    break "C"
}
```

Every reachable path must use `break value` with a compatible type. Norm never inserts null for a missing branch.

## For expressions

Norm uses a foreach-shaped `for`. There is no C-style `for` or `while` syntax.

The binding type may be omitted when the iterable has one statically known element type:

```norm
for index : range(start: 0, end: 10) {
    print(index)
}
```

`Range` infers `int`. Generic iterables infer their binding from the element type, such as `String` for `List<String>`. An explicit binding type is required only when no unique static element type is available.

```norm
int firstEven = for int number : numbers {
    if number % 2 == 0 {
        break number
    }
} else {
    break 0
}
```

The `else` block handles normal exhaustion when the loop did not execute `break value`. The programmer chooses the result for that path.

## Switch expressions

```norm
String name = switch direction {
    case North { break "north" }
    case East { break "east" }
    case South { break "south" }
    case West { break "west" }
}
```

Closed enums are exhaustively checked. Variants may carry data and bind it in a `case` pattern.

## Why break value

Norm deliberately avoids implicit final-expression results. `break value` gives `if`, `for`, and `switch` one consistent value-production rule and keeps exit points visible.

Next: [Reified generics](/en/language/generics).

