# 28 Class identity

Class variables can refer to the same entity or to distinct entities with equal-looking fields.

<<< ../../norm/tests/docs/tour/identity.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/identity.out{text}

`alias` shares the exact object with `first`, so writing through it changes `first.value` to three. A separately constructed object also holds three but has a distinct identity.

Try it: Assign `equalFields = first` and predict the last comparison.

Precise rules: [Reference](/spec/value-identity-semantics).
