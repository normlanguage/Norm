# 43 Implementing an interface

`implements Named` promises a matching `name()` method. A `value` can satisfy the contract as well as a `class`; the caller depends only on `Named`.

<<< ../../norm/tests/docs/tour/interface_implementation.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/interface_implementation.out{text}

Try it: Remove `name()` and inspect the compiler error.

Precise rules: [Reference](/spec/grammar/interfaces).
