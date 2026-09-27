# 44 Default interface methods

`label()` has a body in `Named`, so implementations inherit it while supplying only `name()`. The default calls the implementation through `this`.

<<< ../../norm/tests/docs/tour/default_implementations.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/default_implementations.out{text}

Try it: Add `label()` to `Task` and observe which body runs.

Precise rules: [Reference](/spec/grammar/interfaces).
