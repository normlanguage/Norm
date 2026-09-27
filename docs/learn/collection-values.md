# 21 Collection value semantics

Assigning a built-in collection gives the new binding an independent container value.

<<< ../../norm/tests/docs/tour/collection_values.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/collection_values.out{text}

The copies initially compare equal. Adding to `changed` leaves `original` at two elements; equality then becomes false because their contents differ. Elements that are class objects can still share their own identity.

Try it: Add the same third element to `original` and compare equality again.

Precise rules: [Reference](/spec/value-identity-semantics).
