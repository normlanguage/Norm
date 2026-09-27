# 37 Plain enums

An enum lists a finite set of named alternatives.

<<< ../../norm/tests/docs/tour/plain_enums.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/plain_enums.out{text}

`State.Ready` and `State.Done` are distinct variants of the same type. The variable can change which variant it holds, while the declaration keeps the possible states closed.

Try it: Add a `Paused` variant and assign it in `main`.

Precise rules: [Reference](/spec/enum-design).
