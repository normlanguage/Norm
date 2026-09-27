# 71 Annotation targets

`Label` implements `TypeTarget`, so it may mark a type declaration. `SourceRetention` keeps this annotation's role in compilation without making it runtime-reflectable.

<<< ../../norm/tests/docs/tour/annotation_targets.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/annotation_targets.out{text}

Try it: Move `@Label` onto `main()` and inspect the target diagnostic.

Precise rules: [Reference](/spec/annotations).
