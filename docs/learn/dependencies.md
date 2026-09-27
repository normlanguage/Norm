# 64 Local dependencies

The app declares a direct dependency on `library` in `module.norm`. Package the separate library into the local package repository first, then run the consumer; the app uses the dependency declaration and public `Greeting` type.

Library module description:

<<< ../../norm/tests/docs/dependency/producer/library/module.norm{norm}

Public type:

<<< ../../norm/tests/docs/dependency/producer/library/model/Greeting.norm{norm}

Consumer module description:

<<< ../../norm/tests/docs/dependency/app/module.norm{norm}

Consumer entry:

<<< ../../norm/tests/docs/dependency/app/Main.norm{norm}

From the repository root, prepare the local package and run the app (`--output` points to the local Norm package cache):

```sh
norm package norm/tests/docs/dependency/producer/library/module.norm --output "$HOME/.norm/cache/packages"
norm run norm/tests/docs/dependency/app/Main.norm
```

Expected output:

<<< ../../norm/tests/docs/dependency/expected.out{text}

The library project lives outside the consumer source tree. The checked example also runs a copied consumer without library source, proving that the packaged dependency is used.

Try it: Change the library greeting, package the library again, and rerun the consumer to observe the new value.

Precise rules: [Module and dependencies](/spec/module-system).
