# 34 Flow narrowing

A null check can prove a nullable value non-null inside a branch.

<<< ../../norm/tests/docs/tour/flow_narrowing.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/flow_narrowing.out{text}

In the `else` branch, `value` cannot be null, so `codePointSize()` is a valid String call. Outside the branch, that proof may no longer hold if the value can change.

Try it: Reverse the condition and move the size call into the non-null branch.

Precise rules: [Reference](/spec/type-system).
