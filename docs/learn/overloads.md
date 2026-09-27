# 51 Function overloads

Both declarations use `adjust`, but their parameter types select different bodies. The call is checked against the available signatures.

<<< ../../norm/tests/docs/tour/overloads.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/overloads.out{text}

Try it: Add a Boolean overload and call it.

Precise rules: [Reference](/spec/grammar/functions).
