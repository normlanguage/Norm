# 76 Documenting declarations for agents

`@Document` stores a description on the `greet` declaration. A semantic query reads that same description from the compiler's model, so an agent can discover intent beside the checked signature.

<<< ../../norm/tests/docs/tour/document_annotation.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/document_annotation.out{text}

Query the declaration from the repository root:

```sh
norm query norm/tests/docs/tour/document_annotation.norm greet
```

The result's `query.context.declaration.documentation` is `Returns a greeting for the named person.`

Try it: Change the description, run `norm query` again, and observe the documentation field.

Precise rules: [Reference](/tooling/semantic-query).
