# 35 Safe access

`?.` reads a member only when its receiver exists.

<<< ../../norm/tests/docs/tour/safe_access.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/safe_access.out{text}

For `missing`, `missing?.title` evaluates to null without reading a field. For `present`, it produces the task title; the overall expression remains nullable.

Try it: Add a second nullable member and chain two safe accesses.

Precise rules: [Reference](/spec/grammar/expressions).
