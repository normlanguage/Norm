# 46 Overriding methods

`Friendly` supplies a new `text()` body. The first call is made through a `Greeting` variable but still dispatches to the `Friendly` implementation.

<<< ../../norm/tests/docs/tour/overrides.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/overrides.out{text}

Try it: Replace `Friendly()` with `Greeting()` in the first assignment.

Precise rules: [Reference](/spec/grammar/classes).
