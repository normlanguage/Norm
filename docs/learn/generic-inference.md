# 49 Generic inference

`Entry<>` infers both type arguments from named constructor values. `choose` infers `T` from its arguments without spelling it at the call site.

<<< ../../norm/tests/docs/tour/generic_inference.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/generic_inference.out{text}

Try it: Change the entry's second value and the `right` argument to integers, then inspect the type of `selected`.

Precise rules: [Reference](/spec/formal/generic-inference).
