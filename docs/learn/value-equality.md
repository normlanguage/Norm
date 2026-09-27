# 26 Value equality

Values compare by their contents rather than by creation event.

<<< ../../norm/tests/docs/tour/value_equality.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/value_equality.out{text}

Two separately constructed positions with the same fields compare equal. Changing only `column` makes the third position unequal; nested value fields follow the same content rule.

Try it: Change `other.column` to four and predict both comparisons.

Precise rules: [Reference](/spec/value-identity-semantics).
