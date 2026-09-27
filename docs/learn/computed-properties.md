# 31 Computed properties

A computed property presents accessor behavior with field-like syntax.

<<< ../../norm/tests/docs/tour/computed_properties.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/computed_properties.out{text}

`stored` is the backing field. Reading `value` invokes `get`, while assigning it invokes `set(next)`; the property itself occupies no separate stored field. Remove the setter to make this property read-only.

Try it: Make the setter add one and observe the later read.

Precise rules: [Reference](/spec/grammar/classes).
