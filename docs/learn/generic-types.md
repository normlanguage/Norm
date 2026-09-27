# 47 Generic types

`Box<T>` defines one structure whose `item` type depends on `T`. `Box<Integer>` and `Box<String>` have different checked field types; value equality still compares contents.

<<< ../../norm/tests/docs/tour/generic_types.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/generic_types.out{text}

Try it: Try assigning `label.item` to an `Integer` binding.

Precise rules: [Reference](/spec/grammar/generics).
