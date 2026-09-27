# 62 Packages

The file path `model/Task.norm` matches `package app.model`. `Main.norm` uses `import app.model.Task` to refer to its public value across package boundaries.

Entry file `app/Main.norm`:

<<< ../../norm/tests/docs/projects/packages_steps/app/Main.norm{norm}

Related file `app/model/Task.norm`:

<<< ../../norm/tests/docs/projects/packages_steps/app/model/Task.norm{norm}

Module description `app/module.norm`:

<<< ../../norm/tests/docs/projects/packages_steps/app/module.norm{norm}

Run from the repository root:

```sh
norm run norm/tests/docs/projects/packages_steps/app/Main.norm
```

Expected output:

<<< ../../norm/tests/docs/projects/packages_steps/expected.out{text}

Try it: Move `Task.norm` without changing its package declaration and inspect the path diagnostic.

Precise rules: [Reference](/spec/package-system).
