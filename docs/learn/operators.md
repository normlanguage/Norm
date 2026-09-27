# 05 Operators and comparisons

Arithmetic computes a value; comparisons and Boolean operators compute `Boolean`.

<<< ../../norm/tests/docs/tour/operators.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/operators.out{text}

Multiplication binds before addition, so the total is `8 * 3 + 2 = 26`. `<=` and `>` produce Boolean results. `&&` combines two Boolean values and evaluates its right side only when the left side is true.

Try it: Change `quantity` to `0` and predict all three output lines before running.

Precise rules: [Language Reference](/spec/grammar/operators-precedence).
