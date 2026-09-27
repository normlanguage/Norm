# 80 Collection change notifications

A subscription on a collection field sees in-place `List` mutations as old/new value snapshots. Closing it stops notifications, not later list changes.

<<< ../../norm/tests/docs/tour/collection_changes.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/collection_changes.out{text}

Try it: Replace the list with an equal list and inspect whether it notifies.

Precise rules: [Reference](/spec/declaration-references).
