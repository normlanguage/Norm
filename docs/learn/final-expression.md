# 12 Final-expression returns

A function with a result type may return its final expression without `return`.

<<< ../../norm/tests/docs/tour/final_expression.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/final_expression.out{text}

`square` returns `value * value`. `sumOfSquares` composes two calls and returns their sum. Both function signatures still state `Integer`; the expression at the end must have a compatible type. This is a return rule, not a change to how arguments are passed.

Try it: Change the final expression of `square` to a string and inspect the type error.

Precise rules: [Language Reference](/spec/grammar/functions).
