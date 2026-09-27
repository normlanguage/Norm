# 55 Bound method references

`counter.add` becomes a function value bound to that particular `counter`. Invoking `add(2)` updates the same object's field.

<<< ../../norm/tests/docs/tour/method_references.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/method_references.out{text}

Try it: Create another counter and bind its `add` method separately.

Precise rules: [Reference](/spec/grammar/functions-advanced).
