# 40 Exhaustive matching

A switch over a closed enum must account for every variant.

<<< ../../norm/tests/docs/tour/exhaustive_matching.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/exhaustive_matching.out{text}

All three cases are present, so the result has a path for every State. If a new variant is added, update the switch too; the compiler reports an uncovered case instead of silently choosing one.

This separate example deliberately omits `Done` and does not run:

<<< ../../norm/tests/docs/diagnostics/uncovered_enum.norm{norm}

`norm check` reports `NORM-FLOW-0001: switch is not exhaustive`. The compiler test checks that diagnostic against this file.

Try it: Add the missing `Done` case to the rejected example and check it again.

Precise rules: [Reference](/spec/grammar/patterns).
