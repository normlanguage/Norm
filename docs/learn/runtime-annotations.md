# 73 Runtime annotation metadata

A runtime-retained `Label` can be read from `Task.class`. The lookup returns a nullable `Label?`, so the sample uses safe access and a fallback.

<<< ../../norm/tests/docs/tour/runtime_annotations.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/runtime_annotations.out{text}

Try it: Remove `@Label` and observe the fallback.

Precise rules: [Reference](/spec/annotations).
