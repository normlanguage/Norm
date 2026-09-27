# 14 Iteration

Use `for` to visit each element without managing an index yourself.

<<< ../../norm/tests/docs/tour/iteration.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/iteration.out{text}

The first binding receives the element; the optional second binding receives its zero-based index. The loop body runs once for each element, in collection order.

Try it: Remove the index binding and print only the steps.

Precise rules: [Reference](/spec/grammar/loops).
