# 36 Null fallback

`??` uses its right operand only when the left operand is null.

<<< ../../norm/tests/docs/tour/fallback.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/fallback.out{text}

The first call prints `Norm` without invoking `fallback()`. The second prints `computed` before `guest`, proving the fallback function is evaluated only for absence.

Try it: Make `present` null too and count how often the fallback runs.

Precise rules: [Reference](/spec/grammar/expressions).
