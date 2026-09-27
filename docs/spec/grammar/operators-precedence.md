# Operator Precedence

Norm operators have fixed semantics and cannot be overloaded by users. This table lists the current precedence draft, highest to lowest.

| Level | Operators | Associativity |
| --- | --- | --- |
| 1 | `()`, `[]`, `.`, `?.` | Left |
| 2 | Unary `!`, `-`, `+` | Right |
| 3 | `*`, `/`, `%` | Left |
| 4 | `+`, `-` | Left |
| 5 | `<`, `<=`, `>`, `>=`, `is`, `as` | Nonchainable |
| 6 | `==`, `!=` | Nonchainable |
| 7 | `&&` | Left, short-circuiting |
| 8 | `||` | Left, short-circuiting |
| 9 | `??` | Right, short-circuiting |
| 10 | `=` | Right |

```norm
Boolean accepted = ready && count > 0
Integer total = base + quantity * price
```

Comparisons cannot be chained:

```norm
0 < value < 10 // compile error
0 < value && value < 10 // valid
```

`&&`, `||`, and `??` evaluate left to right and short-circuit. Assignment is not an ordinary value expression and cannot appear in a condition. Use parentheses when an expression is ambiguous or a reader would have to reconstruct the precedence.

[Block call chains](/spec/grammar/functions-advanced#block-call-chains) have the precedence and left associativity of ordinary postfix member calls and introduce no infix operator.
