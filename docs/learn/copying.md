# 29 Copying a class

Call `copy()` when a new top-level class identity is needed.

<<< ../../norm/tests/docs/tour/copying.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/copying.out{text}

The copy starts with the original field value, then changes independently. Equality remains false because class equality uses identity. Class objects held in fields remain shared unless copied separately.

Try it: Give the class a nested class field and inspect shallow-copy behavior.

Precise rules: [Reference](/spec/value-identity-semantics).
