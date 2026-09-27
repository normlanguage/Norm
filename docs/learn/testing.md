# 65 Tests

`@Test` associates `doublesSeven` with `double.function`. `require` checks the behavior; `norm test` discovers and runs the test separately.

<<< ../../norm/tests/docs/tour/testing.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/testing.out{text}

Run the test from the repository root:

```sh
norm test norm/tests/docs/tour/testing.norm --format json
```

The JSON result reports one discovered and one passing test.

Try it: Change expected value 14 to 15 and run `norm test` to inspect the failure.

Precise rules: [Reference](/tooling/verification).
