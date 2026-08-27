# Annotation 与 Reflect

Annotation 为声明附加结构化常量元数据。Reflect 是读取类型 metadata 的显式、类型安全入口。

```norm
annotation Label targets(type) retention(runtime) {
    String text
}

@Label(text: "two-dimensional coordinate")
value Point {
    Integer x
    Integer y
}
```

## 最小反射 API

```norm
Type<Point> point = reflect<Point>()
String name = point.name()
Label? label = point.annotation<Label>()
```

- `reflect<T>()` 返回 reified `Type<T>`；
- `Type<T>.name()` 返回稳定的 Norm 类型显示名；
- `Type<T>.annotation<A>()` 只接受 annotation 类型，并返回该类型目标上的 runtime annotation；目标没有该 annotation 时返回 `null`。

0.12 只查询类型目标。其他目标的 binary/runtime metadata 仍保存在 Core 中，后续 API 可以在同一 metadata 模型上扩展。

Annotation 和 Reflect 不允许改写 AST、注入成员、拦截调用或改变类型检查。需要行为时使用普通函数和显式注册 API。

完整规则见 [Annotation 规范](/spec/annotations)。
