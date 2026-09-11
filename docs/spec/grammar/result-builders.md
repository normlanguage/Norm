# 结果构建器

结果构建器让内容块按顺序累计表达式，生成一个强类型结果。它适用于 UI、文本及其他领域对象。

```norm
Row {
  Button("全部") { this.reload(null) }
  Button("未完成") { this.reload(false) }
  Button("已完成") { this.reload(true) }
}
```

参数以 `@BuildWith(Builder.class)` 标记，类型必须是零参数 `Function<Result()>`。构建器实现 `std.build.ResultBuilder<Element, Result>`，提供可无参数调用的构造入口。公共声明以 [builders.norm](../../../norm/stdlib/std/build/builders.norm) 为准。

每次执行内容回调都会创建独立构建器。表达式语句按 `Element` 检查类型并调用 `add`；末尾调用 `finish`，包括空块。`if` 只累计选中分支，`for` 按迭代顺序累计；局部声明与赋值保留原有行为，不贡献元素。异常在原位置传播。内容块不允许 `return` 或带值的 `break`。

嵌套的事件回调保持普通函数语义。直接传入已经存在的函数值时，不转换该函数体。构建器注解不改变普通集合的分隔符规则。

前端转换统一由 [ResultBuilderLowering](../../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/ResultBuilderLowering.java) 提供；编译器不识别特定 UI 组件名称。执行、泛型、诊断和增量编译的契约测试见 [ResultBuilderExecutionTest](../../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ResultBuilderExecutionTest.java)。
