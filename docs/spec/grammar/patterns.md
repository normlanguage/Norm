# Pattern Matching

Patterns occur only in explicit matching positions such as switch cases. They test shape and bind local names; they are not arbitrary Boolean expressions.

## Pattern forms

Initial patterns include variants, typed bindings, wildcard `_`, literals, and `null`. Each data position of a variant accepts another complete pattern, so patterns may nest recursively.

```norm
enum Tree<T> {
    Leaf(T value),
    Branch(Tree<T> left, Tree<T> right)
}

case Branch(Leaf(Integer value), _) {
    printLine(value)
}
```

The variant name must belong to the enum at that position. Argument order must match the variant declaration. Trailing data positions with defaults may be omitted, equivalent to `_`. `Integer value` is a typed binding: on a match, it binds the matched value as the declared type under the name `value`, visible only within that case block.

`_` matches anything and binds no name. A literal matches under the type's built-in language equality and must be compatible with the position's static type. `null` matches only a null value in a nullable position.

```norm
case Leaf(0) { printLine("zero") }
case Leaf(null) { printLine("missing nullable value") }
case _ { printLine("other") }
```

A typed binding may use the matched value's static type or a nominal subtype. A subtype binding checks the dynamic type and binds the narrowed value. Member shapes are not matched.

A nullable binding matches both null and non-null values of its type: `String? text` may bind null, while `String text` matches only non-null strings. A nullable subtype binding does not cover other non-null subtypes; exhaustiveness and unreachable-branch checks use the same rules.

Generic type patterns retain all type arguments: `Box<Integer>` does not match `Box<String>`, while `Box<T>` uses the current invocation's reified type argument. The following executable example also combines enum patterns, switch results, and `if` results:

<<< ../../../norm/tests/docs/language/matching_results.norm{norm}

## Matching process

One pattern is checked outside-in and left-to-right at each level. A failure leaves no local binding or other observable state. Cases select the first matching pattern in source order. A case completely covered by earlier patterns is unreachable and causes a compile error. Patterns inspect only enum variants, nominal types, and values; they invoke no user-defined matching protocol.
