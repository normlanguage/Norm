# 61 Modules

`module.norm` names the module, its version, and exported source files. The CLI discovers this description when running an entry file inside the module. `package app` gives the entry a top-level package name; a later lesson adds nested packages.

Entry file `app/Main.norm`:

<<< ../../norm/tests/docs/projects/modules/app/Main.norm{norm}

Module description `app/module.norm`:

<<< ../../norm/tests/docs/projects/modules/app/module.norm{norm}

Run from the repository root:

```sh
norm run norm/tests/docs/projects/modules/app/Main.norm
```

Expected output:

<<< ../../norm/tests/docs/projects/modules/expected.out{text}

Try it: Change the module name without renaming its directory and inspect the mismatch diagnostic. Rename the module directory to match the name, update the run path, and try again.

Precise rules: [Reference](/spec/module-system).
