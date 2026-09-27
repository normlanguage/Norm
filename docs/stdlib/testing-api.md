# Testing API

`std.testing.Test` marks a top-level, nongeneric, parameterless `Void` function that can run independently. Normal return means success; an unhandled exception or assertion failure means failure. [`testing/tests.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/tests.norm) defines the annotation and association fields.

```norm
package sample.math

import std.testing.Test
import std.math.clamp

@Test(functions: [clamp.function])
Void clampBelowMinimum() {
  require(condition: clamp(value: -2, minimum: 0, maximum: 10) == 0, message: "lower bound")
}
```

Use `@Test` when no documentation association is needed. `types`, `functions`, and `fields` refer to declarations under test, letting the API docs build reverse links. An association does not indicate coverage or passing status. A reference to an overloaded function still needs a function type that identifies the target uniquely.

## Execution

```bash
norm test path/to/module
norm test path/to/module --filter sample.math
norm test path/to/module --filter sample.math.clampBelowMinimum
norm test path/to/test.norm
```

A filter matches a full function name or package prefix; the command fails if no tests match. Each Norm test uses an independent execution context and runtime resources; relative file paths resolve from the test source directory. The editor offers `Run Test` on a test declaration and invokes the same CLI entry point.

See the [module system](/spec/module-system#source-set) for source sets and package ownership. Standard-library tests are in [`std/tests/test`](https://github.com/normlanguage/Norm/tree/main/norm/stdlib/std/tests/test), the test source set of the `std` module.

## Assertions and output

Composable predicates are in [`testing/predicates.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/predicates.norm). They return Boolean values that `require` can check.

[`testing/output.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/output.norm) defines the expected-output protocol. When a test declares nonempty expected output, the runner compares it against the entire actual output of that test. Without an expected output declaration, printing is observational only.

Test execution is aggregated through the JUnit Platform. Compilation, resource setup, and Core calls reuse the application execution path. Independent programs under [`norm/tests`](https://github.com/normlanguage/Norm/tree/main/norm/tests) continue to accept the language entry point and multimodule startup.
