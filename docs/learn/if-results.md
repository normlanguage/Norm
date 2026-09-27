# 11 `if` as a result

An `if/else` expression can produce one value from either branch.

<<< ../../norm/tests/docs/tour/if_results.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/if_results.out{text}

Each branch's final expression is a `String`, so the whole `if` has type `String` and can be returned. When a result is required, both paths must produce compatible values; an absent `else` would leave one path without a result.

Try it: Change one branch to an `Integer` and inspect how the result type is checked.

Precise rules: [Language Reference](/spec/grammar/expressions).
