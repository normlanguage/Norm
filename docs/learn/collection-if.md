# 23 Collection `if` elements

A collection literal can include an element only when a condition holds.

<<< ../../norm/tests/docs/tour/collection_if.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/collection_if.out{text}

Here `includeExtra` is false, so `inspect` contributes no element at all; `run` becomes index one. This is different from an ordinary `if` expression that chooses one of two values.

Try it: Set the flag to true and predict the list size and index-one item.

Precise rules: [Reference](/spec/grammar/literals).
