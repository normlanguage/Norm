# 63 Visibility

`heading` is public and can be imported into `Main.norm`. `prefix` is private to its declaring file; callers use the public function instead of importing that helper.

Entry file `app/Main.norm`:

<<< ../../norm/tests/docs/projects/visibility/app/Main.norm{norm}

Related file `app/tools/Format.norm`:

<<< ../../norm/tests/docs/projects/visibility/app/tools/Format.norm{norm}

Module description `app/module.norm`:

<<< ../../norm/tests/docs/projects/visibility/app/module.norm{norm}

Run from the repository root:

```sh
norm run norm/tests/docs/projects/visibility/app/Main.norm
```

Expected output:

<<< ../../norm/tests/docs/projects/visibility/expected.out{text}

Try it: Try importing `prefix` in `Main.norm` and inspect the visibility diagnostic.

Precise rules: [Reference](/spec/module-system).
