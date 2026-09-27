# 87 A packaged library

This example imports functions from the [Norm Commons Lang adapter](https://github.com/normlanguage/commons-lang). Its inline `Module module()` declares a direct dependency on `commons.lang`; the API comes from a packaged library, not a copied source file.

<<< ../../norm/tests/docs/libraries/commons-lang/hello.norm{norm}

With the [development toolchain](/design/distribution-source-build), prepare the current library source once from the Norm repository root, then run the consumer:

```sh
git clone https://github.com/normlanguage/commons-lang.git .tmp/commons-lang
norm package .tmp/commons-lang/commons/lang/module.norm --output "$HOME/.norm/cache/packages"
norm run norm/tests/docs/libraries/commons-lang/hello.norm
```

Expected output:

<<< ../../norm/tests/docs/libraries/commons-lang/hello.out{text}

The example is checked against a locally packaged current library; an older public library package may use a different binding format. For a longer combined use case, see the [import reference](/spec/import-system).

Try it: Change the raw heading's spacing and observe `stringUtilsNormalizeSpace`.

Precise rules: [Module dependencies](/spec/module-system).
