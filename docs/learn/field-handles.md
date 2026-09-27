# 78 Field handles

`bind(task)` combines a typed field descriptor with one class instance. The `FieldHandle<String>` can read and write that object's field later; it is distinct from lexical `ref<T>`.

<<< ../../norm/tests/docs/tour/field_handles.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/field_handles.out{text}

Try it: Bind the same field on a second task and compare results.

Precise rules: [Reference](/spec/declaration-references).
