# 77 Lexical references

`&count` borrows the writable local location as `ref<Integer>`; `*target` reads and replaces the stored value. The reference stays within the call and cannot escape as a return value.

<<< ../../norm/tests/docs/tour/lexical_references.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/lexical_references.out{text}

Try it: Change `increment` to return `ref<Integer>` and add `return target`; the compiler rejects `ref` in a return type (`NORM-TYPE-0001`).

Precise rules: [Reference](/spec/grammar/references).
