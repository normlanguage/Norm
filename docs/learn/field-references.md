# 69 Field declaration references

`Task.title.field` names the field declaration, independent of one object. `read` and `write` receive a concrete `Task` instance and preserve the field's `String` type.

<<< ../../norm/tests/docs/tour/field_references.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/field_references.out{text}

Try it: Create a second task and read its title through the same descriptor.

Precise rules: [Reference](/spec/declaration-references).
