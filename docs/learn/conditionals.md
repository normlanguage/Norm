# 06 Conditional execution

An `if/else` statement chooses which block of work to run.

<<< ../../norm/tests/docs/tour/conditionals.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/conditionals.out{text}

Conditions must have type `Boolean`. Branches are checked in order; the first true branch runs and the others are skipped. This lesson uses `if` for actions. A later lesson uses `if` itself as a value.

Try it: Set `score` to `95`, then `40`, and identify which branch runs each time.

Precise rules: [Language Reference](/spec/grammar/statements).
