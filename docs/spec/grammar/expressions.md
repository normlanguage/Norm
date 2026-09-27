# Expression Syntax

## Basic forms

```text
Expression := Literal
            | Identifier
            | MemberAccess
            | Call
            | Index
            | UnaryExpression
            | BinaryExpression
            | IfExpression
            | ForExpression
            | SwitchExpression
```

## Members, calls, and indexing

```norm
Point point = Point(x: 2, y: 4)
Integer x = point.x
String first = names[0]
```

Multiparameter calls use `name: value`. A single argument may omit its name; in a multiparameter call, a bare identifier may omit its label if it has the same name as the matching parameter. Other positional arguments are invalid, while named arguments may appear in any order. Receivers, arguments, and indices evaluate once in source order. Safe navigation, implicit await, and dynamic member lookup are not part of this grammar.

## Operations

```norm
Integer total = base + quantity * price
Boolean accepted = ready && total > 0
```

Operators are fixed and cannot be overloaded. Logical operations accept Boolean only and short-circuit. Assignment is a separate statement and produces no value for a larger expression.

`throw` may appear in an expression position expecting a value. Its operand must be a non-null `Exception`. It produces no value, following the same propagation and `catch/finally` rules as a throw statement; the surrounding expected type determines its result type.

```norm
var todo = repository.findById(id) ?? throw Exception(message: "任务已不存在")
```

`??` evaluates its right side only when the left side is null. After a successful query in this example, `todo` has a non-null type and no exception is created. The query expression runs only once.

## If expressions

```norm
String state = if active {
    "active"
} else {
    "inactive"
}
```

The condition must be Boolean. Branches produce results through a trailing expression or explicit `break value`. Every normally completing path must yield a value; `return` in a branch still exits the surrounding function. Branches retain ordinary if type narrowing and local scope.

Braces may be omitted for a branch containing only one result expression: `if editing "" else task.title`. This does not depend on newline placement. Branches with declarations, assignments, or several statements still use braces. An `else` belongs to the nearest unmatched `if`.

Parentheses around a condition mark its end clearly, as in `if (ready) -1 else 0`. Without them, the condition follows ordinary expression parsing. If a branch starts with a minus sign, parenthesis, or another symbol that might continue the condition, parenthesize the whole condition or retain branch braces.

## For expressions

```norm
Integer found = for Integer number : numbers {
    if number > 0 { break number }
} else {
    break 0
}
```

## Switch expressions

```norm
String text = switch token {
    case Name(String value) { break value }
    case End { break "end" }
}
```

Every possibly normally completing path of a control-flow expression must produce a compatible value; null is not inserted implicitly. See [advanced function rules](functions-advanced.md) for trailing-return behavior in callable bodies.
