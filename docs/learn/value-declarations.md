# 25 Value declarations

A `value` groups immutable fields into a typed data value.

<<< ../../norm/tests/docs/tour/value_declarations.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/value_declarations.out{text}

Construction names each field. After creation, those fields are read-only; use another value when coordinates change. This makes a position describe data rather than a changing entity.

Try it: Create a second position with a different column.

Precise rules: [Reference](/spec/grammar/values).
