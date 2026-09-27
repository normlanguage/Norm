# 50 Matching generic types

The two `Box` cases distinguish `Box<Integer>` from `Box<String>` at runtime and expose an appropriately typed `item` inside each branch.

<<< ../../norm/tests/docs/tour/retained_generic_matching.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/retained_generic_matching.out{text}

Try it: Add a `Box<Boolean>` candidate and predict the final branch.

Precise rules: [Reference](/spec/grammar/patterns).
