# Statement Syntax

Statements perform actions without serving as a value for an enclosing expression. Core statements include variable declarations, assignments, expression statements, return, break, continue, throw, and control-structure statements.

## Variables and assignments

```norm
Integer count = 0
count = count + 1
```

A non-null local variable must be initialized at declaration. An assignment target must be a writable local, class field, Array/List element, or writable location explicitly defined by an API. Value fields are not assignable.

## Expression statements

A function or method call can be a statement:

```norm
logger.info(event: "started")
```

Discarding a call with an important Result return value should warn unless the caller explicitly uses the standard-library discard function to express intent.

## Return

`return` ends the current function. A Void function uses `return` or reaches its end normally; other functions use `return expression`. A return inside an anonymous nested function does not return from the enclosing function.

## Break and Continue

A valueless break ends the nearest loop statement; continue starts the next iteration. `break expression` is valid only inside an if, for, or switch expression being evaluated.

## Throw

`throw expression` ends the current path and starts exception lookup. A throwing path need not satisfy the current function's ordinary return-value rule.

Unreachable statements produce diagnostics. A variable's scope starts after its declaration and ends with the containing block.
