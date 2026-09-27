# 04 String interpolation

Use `${expression}` to place a typed expression inside a string.

<<< ../../norm/tests/docs/tour/interpolation.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/interpolation.out{text}

The braces evaluate `name` or `completed` where the string is built. The source remains one `String` expression, and the embedded value is converted for display. Interpolation does not change the type of `completed`.

Try it: Try `${completed + 1}` and compare the output.

Precise rules: [Language Reference](/spec/grammar/lexical).
