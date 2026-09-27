# 27 Class declarations

A `class` models an entity with fields and behavior that can change.

<<< ../../norm/tests/docs/tour/class_declarations.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/class_declarations.out{text}

The labeled construction supplies `value`. Calling `increment()` mutates the same counter, and a later field read observes one. Methods keep state changes close to the entity they govern.

Try it: Call `increment()` twice and predict the result.

Precise rules: [Reference](/spec/grammar/classes).
