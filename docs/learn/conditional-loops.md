# 15 Conditional loops

A condition-style `for` repeats while its Boolean condition remains true.

<<< ../../norm/tests/docs/tour/conditional_loops.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/conditional_loops.out{text}

The condition is checked before every iteration, so a false initial condition runs the body zero times. Updating `remaining` in the body makes this loop terminate.

Try it: Change the initial value to zero and predict the output.

Precise rules: [Reference](/spec/grammar/loops).
