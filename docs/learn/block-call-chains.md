# 59 Chaining block calls

`produce { 7 }` supplies a callback that creates a value; `map { item * 2 }` transforms it. Each block's expected function type guides its parameter and result.

<<< ../../norm/tests/docs/tour/block_call_chains.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/block_call_chains.out{text}

Try it: Change the first block to return 9 and trace the result.

Precise rules: [Reference](/spec/grammar/functions-advanced).
