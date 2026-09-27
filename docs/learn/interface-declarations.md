# 42 Interface declarations

An interface specifies a callable contract. `Named` requires `name()`; `show` accepts any implementation through the interface type.

<<< ../../norm/tests/docs/tour/interface_declarations.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/interface_declarations.out{text}

Try it: Change `Task.title` and watch the same `show` function use the new name.

Precise rules: [Reference](/spec/grammar/interfaces).
