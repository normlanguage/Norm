# 83 Control flow in result builders

Only the selected `if` branch contributes an element. The `for` loop contributes one element per iteration, preserving order in the final result.

<<< ../../norm/tests/docs/tour/builder_control_flow.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/builder_control_flow.out{text}

Try it: Make the condition false and predict the resulting text.

Precise rules: [Reference](/spec/grammar/result-builders).
