# 41 Nested patterns

A pattern may match a variant inside another variant's payload.

<<< ../../norm/tests/docs/tour/nested_patterns.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/nested_patterns.out{text}

The first case extracts the code only when an Updated event contains Sent. The next case handles other Updated deliveries; the final case handles Ignored. Cases are tested in order without falling through.

Try it: Swap the first two cases and observe whether the compiler flags an unreachable case.

Precise rules: [Reference](/spec/grammar/patterns).
