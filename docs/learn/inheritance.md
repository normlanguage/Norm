# 45 Class inheritance

`NamedCounter extends Counter` keeps the base fields and adds `name`. Its constructor calls `super(initial: initial)` before assigning the new field.

<<< ../../norm/tests/docs/tour/inheritance.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/inheritance.out{text}

Try it: Change the initial count and verify the inherited value.

Precise rules: [Reference](/spec/grammar/classes).
