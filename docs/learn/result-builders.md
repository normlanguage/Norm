# 82 Result builders

`@BuildWith(Words.class)` transforms expression statements in the callback into ordered `add` calls. `finish()` returns the accumulated `String`; each callback execution gets a fresh builder.

<<< ../../norm/tests/docs/tour/result_builders.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/result_builders.out{text}

Try it: Add another string expression to the block.

Precise rules: [Reference](/spec/grammar/result-builders).
