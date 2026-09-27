# 60 Imports

An `import` gives this file a short name for a public declaration in another package. Here `abs` comes from the standard library's `std.math`; no project setup is needed for this one-file program.

<<< ../../norm/tests/docs/tour/imports.norm{norm}

Run from the repository root:

```sh
norm run norm/tests/docs/tour/imports.norm
```

Expected output:

<<< ../../norm/tests/docs/tour/imports.out{text}

Try it: Remove the import and inspect the unresolved name error.

Precise rules: [Import system](/spec/import-system).
