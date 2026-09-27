# 67 Cleanup with finally

`finally` runs when the `try` exits, even after the return value is ready. The output order shows cleanup before control returns to the caller.

<<< ../../norm/tests/docs/tour/cleanup.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/cleanup.out{text}

Try it: Change `return 7` to `return 9` and confirm that cleanup order stays the same.

Precise rules: [Reference](/spec/grammar/try-catch).
