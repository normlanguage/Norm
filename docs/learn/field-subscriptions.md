# 79 Field subscriptions

`onChange` receives the previous and new field values after an assignment. `close()` stops later notifications, while the assignment itself still updates the field.

<<< ../../norm/tests/docs/tour/field_subscriptions.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/field_subscriptions.out{text}

Try it: Assign `Done` twice before closing and observe equal-value filtering.

Precise rules: [Reference](/spec/declaration-references).
