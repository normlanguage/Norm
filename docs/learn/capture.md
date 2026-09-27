# 54 Capturing values

The returned lambda keeps access to `factor` after `multiplier` finishes. Each call supplies a new `number` while using the captured factor.

<<< ../../norm/tests/docs/tour/capture.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/capture.out{text}

Try it: Create a second multiplier with factor 5.

Precise rules: [Reference](/spec/grammar/functions-advanced).
