# 22 Collection `for` elements

A collection literal can generate elements from another iterable.

<<< ../../norm/tests/docs/tour/collection_for.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/collection_for.out{text}

The parenthesized `for` head belongs inside `[]`: it contributes `number * 2` once per source element. This builds a new list, unlike the statement loop in the iteration lesson.

Try it: Filter the generated list in the next lesson, then compare the outputs.

Precise rules: [Reference](/spec/grammar/literals).
