# Loop Syntax

Norm uses `for` for both traversal and conditional loops. Traversal accepts only a value explicitly implementing the standard-library `Iterable<T>` interface and iterates through its `Iterator<T>`.

```norm
for String name : names {
    printLine(name)
}
```

## Syntax

```text
For := ForEach | ConditionalFor
ForEach := "for" Type? Identifier ("," Identifier)? ":" Expression Block ("else" Block)?
ConditionalFor := "for" Expression Block
```

The iterable expression evaluates once. Loop variables bind at the start of each iteration and are invisible outside the loop body.

The second name is a zero-based Integer index; the value name always comes first:

```norm
for value,index : values {
    printLine(index)
    printLine(value)
}
```

On `continue`, the index advances with the iteration; `break` ends the loop immediately.

The loop-variable type may be omitted when the iterable has a unique, statically known element type:

```norm
for index : range(start: 0, end: 10) {
    printLine(index)
}
```

`Range` implements `Iterable<Integer>`; `List<T>`, `Array<T>`, and `Set<T>` derive element types from their `Iterable<T>` argument. An explicit loop-variable type is required only when no unique static element type can be determined.

## Conditional loops

```norm
for digits.size() > 1 && digits.last() == 0 {
    digits.removeLast()
}
```

The condition must be Boolean and is reevaluated before each iteration. If initially false, the loop executes zero times. `continue` proceeds to the next condition check. The execution backend checks cancellation in each iteration.

## Control transfer

`continue` ends the current iteration. A valueless `break` ends a loop used as a statement.

```norm
for Integer number : numbers {
    if number < 0 { continue }
    if number == 0 { break }
    printLine(number)
}
```

## For expressions

This section specifies the planned value-position form. The current compiler accepts statement loops and collection-literal `for` elements, but rejects `for` in a value position; see [Status](/status).

When a loop appears in a value position, successful paths use `break value`, and the `else` produces a value on normal exhaustion:

```norm
Integer match = for Integer number : numbers {
    if number % 2 == 0 { break number }
} else {
    break -1
}
```

Expression loops cannot use a valueless `break`. All reachable completion paths must produce compatible types; the compiler does not implicitly supply `null`.
