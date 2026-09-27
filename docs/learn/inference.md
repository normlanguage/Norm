# 03 Type inference

`var` lets the initializer determine a local variable's static type.

<<< ../../norm/tests/docs/tour/inference.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/inference.out{text}

`count` is inferred as `Integer` and `title` as `String`. Reassignment still checks the original type; `var` does not make a variable dynamically typed. An initializer such as `null` or `[]` needs a type supplied by context, so it cannot stand alone after `var`.

Try it: Try changing `count = count + 1` to a string assignment and inspect the diagnostic.

Precise rules: [Language Reference](/spec/type-inference).
