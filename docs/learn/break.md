# 16 Break

A valueless `break` leaves the current statement loop immediately.

<<< ../../norm/tests/docs/tour/break.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/break.out{text}

The item `stop` is never printed, and neither is the following item: control resumes after the loop. The final line proves execution continues outside it.

Try it: Move `stop` to the last position and compare the output.

Precise rules: [Reference](/spec/grammar/loops).
