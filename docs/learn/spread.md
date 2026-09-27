# 24 Spread in collections

`...` inserts the elements of an iterable at one position in a collection literal.

<<< ../../norm/tests/docs/tour/spread.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/spread.out{text}

The `middle` list contributes `run` and `check` in order, and `tail` contributes `share` at the end. Neither source list becomes one nested element.

Try it: Spread an empty list and observe the new size.

Precise rules: [Reference](/spec/grammar/literals).
