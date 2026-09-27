# 39 Switch results

`switch` can produce a result while extracting an enum variant's payload.

<<< ../../norm/tests/docs/tour/switch_results.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/switch_results.out{text}

Each `case` binds the matching payload and exits with `break` followed by a string. The function returns that switch result; a statement switch would not need a result value.

Try it: Change the Success branch to include a label before the count.

Precise rules: [Reference](/spec/grammar/switch).
