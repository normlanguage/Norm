# 08 Lambda and Extension

Functions can be passed as values. An extension changes call syntax without changing the target type or dynamic dispatch.

<<< ../../norm/tests/docs/tour/08_lambdas_extensions.norm{norm}

Output:

```text
12
Norm
```

## Function values and lambdas

The complete function type is `Function<ReturnType(ParameterTypes...)>`. A callable declaration in a parameter position is shorthand for the same type. A lambda can obtain parameter and result types from its expected function type, or declare parameter types explicitly.

A lambda's last expression forms its result; a named function still requires an explicit `return`. Lambdas can capture outer locals, parameters, and `this`, but captured bindings must be effectively final.

A bound method reference uses `receiver.method`, while an ordinary function can be assigned directly to a compatible function type. Use `Owner.method.function` for an unbound declaration reference.

## Extension functions

An extension's first parameter is its receiver and is omitted from the argument list in member-style calls. Extensions must be imported explicitly and resolve statically; a real instance method takes precedence by name. An ordinary function does not become an extension merely because its first parameter has the same type.

See the [advanced function rules](/spec/grammar/functions-advanced) and [extension syntax](/spec/grammar/functions#extension-functions) for complete rules.

Previous: [Collections and iteration](/learn/collections). Next: [Errors and exceptions](/learn/errors).
