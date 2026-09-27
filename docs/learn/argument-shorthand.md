# 10 Argument shorthand

A local identifier can stand for its matching parameter label.

<<< ../../norm/tests/docs/tour/argument_shorthand.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/argument_shorthand.out{text}

In `remaining(total, completed)`, each identifier matches the parameter at the same position, so the call is equivalent to `remaining(total: total, completed: completed)`. The explicit second call shows the full form. A bare value with a different name is not shorthand.

Try it: Rename the local `completed` to `done` and use an explicit label to keep the call valid.

Precise rules: [Language Reference](/spec/grammar/functions).
