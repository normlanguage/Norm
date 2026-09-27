# 33 Nullable values

A `?` on a type permits `null`; the type without `?` does not.

<<< ../../norm/tests/docs/tour/nullable_values.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/nullable_values.out{text}

`label` can return a string or absence, so its result is `String?`. Both bindings retain that nullable type even when one call currently returns a string; use a check or a fallback before requiring a non-null string.

Try it: Change the false branch to a string and compare the two null checks.

Precise rules: [Reference](/spec/type-system).
