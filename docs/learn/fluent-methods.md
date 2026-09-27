# 32 Fluent methods

A method may return `this` so the caller can continue operating on the same object.

<<< ../../norm/tests/docs/tour/fluent_methods.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/fluent_methods.out{text}

Each `add` changes the counter, then returns that very counter. The second call therefore receives the first call's result; the final value includes both additions.

Try it: Split the chain into two statements and check the same output.

Precise rules: [Reference](/spec/grammar/classes).
