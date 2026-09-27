# 68 Type declaration references

`Task.class` is a typed `Class<Task>` descriptor for the declaration. Its metadata tells us the type name and whether it has value semantics.

<<< ../../norm/tests/docs/tour/type_references.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/type_references.out{text}

Try it: Replace `Task.class` with `String.class` and compare the metadata.

Precise rules: [Reference](/spec/declaration-references).
