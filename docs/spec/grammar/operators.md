# Operators

Norm has a limited set of operators that users cannot overload. The same symbol retains the same category of meaning across types.

## Arithmetic

`+`, `-`, `*`, `/`, and `%` apply to numeric types explicitly supported by the specification. Unary `+` and `-` perform no implicit type conversion. The numeric specification fixes integer division, division-by-zero, and overflow behavior; optimization level cannot change them.

String does not use `+` to implicitly concatenate arbitrary objects; string templates handle formatting.

## Comparison

`==` and `!=` compare according to data category: values use structural equality, classes use object identity, and refs use storage-location identity. `<`, `<=`, `>`, and `>=` apply only to numerics with built-in language ordering; other types compare explicitly through Comparable methods. Equatable, Comparable, and other standard-library protocols neither overload nor change operator semantics.

Comparing values pointed to by refs requires explicit reads; location identity must not be confused with content equality.

## Logic

`!`, `&&`, and `||` accept Boolean only. `&&` and `||` evaluate left to right and short-circuit; numbers, Strings, and nullable values do not implicitly become Boolean.

## Nullable

`receiver?.member` reads a member or calls a method only when the receiver is non-null. The receiver is evaluated once; method arguments are not evaluated on the null branch. The result has the nullable form of the member result. A safe call returning Void still has type Void.

`nullable ?? fallback` returns the left value if non-null; otherwise it evaluates and returns the fallback. Sides evaluate left to right, and the fallback type must be compatible with the non-null part of the left side.

## Type operations

`is` checks a nominal runtime type and can narrow control flow. `as` performs an explicit conversion whose failure behavior is defined by type-system rules.

See [operator precedence](/spec/grammar/operators-precedence) for the complete ordering.
