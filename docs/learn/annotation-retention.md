# 72 Annotation retention

`CompileHint` has source retention; `CatalogHint` has binary retention. Neither is returned by runtime annotation lookup. Binary metadata remains available to compiler and documentation tooling; runtime reflection requires `RuntimeRetention`.

<<< ../../norm/tests/docs/tour/annotation_retention.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/annotation_retention.out{text}

Try it: Change `CatalogHint` to `RuntimeRetention` and predict the second line.

Precise rules: [Reference](/spec/annotations).
