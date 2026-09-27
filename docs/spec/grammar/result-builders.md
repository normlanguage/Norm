# Result Builders

Result builders accumulate expressions from a content block in order and produce one strongly typed result. They apply to UI, text, and other domain objects.

```norm
Row {
  Button("全部") { this.reload(null) }
  Button("未完成") { this.reload(false) }
  Button("已完成") { this.reload(true) }
}
```

A parameter annotated with `@BuildWith(Builder.class)` must have type `Function<Result()>` with no parameters. The builder implements `std.build.ResultBuilder<Element, Result>` and offers a construction entry point callable without arguments. [builders.norm](../../../norm/stdlib/std/build/builders.norm) defines public declarations.

Each execution of the content callback creates a separate builder. Expression statements are checked as `Element` and passed to `add`; `finish` is called at the end, even for an empty block. An `if` accumulates only its selected branch; a `for` accumulates in iteration order. Local declarations and assignments retain their ordinary behavior and contribute no element. Exceptions propagate at their original positions. A content block disallows `return` and value-bearing `break`.

Nested event callbacks retain ordinary function semantics. Passing an existing function value directly does not transform that function body. Builder annotations do not change separator rules for ordinary collections.

[ResultBuilderLowering](../../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/ResultBuilderLowering.java) provides the one frontend transformation; the compiler recognizes no particular UI component names. [ResultBuilderExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ResultBuilderExecutionTest.java) covers execution, generics, diagnostics, and incremental compilation.
