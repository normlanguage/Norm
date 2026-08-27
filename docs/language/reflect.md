# Annotation 与 Reflect

Reflect 是读取 runtime Annotation 的显式、类型安全入口。

```norm
import std.annotation.TypeTarget
import std.annotation.RuntimeRetention

annotation Label implements TypeTarget, RuntimeRetention {
  String text
}

@Label(text: "two-dimensional coordinate")
value Point {
  Integer x
  Integer y
}
```

```norm
Type<Point> point = reflect<Point>()
String name = point.name()
Label? label = point.annotation<Label>()
```

- `reflect<T>()` 返回 reified `Type<T>`；
- `Type<T>.name()` 返回稳定的 Norm 类型显示名；
- `Type<T>.annotation<A>()` 只接受 Annotation 类型，读取类型目标上的 `RuntimeRetention` 实例；不存在时返回 `null`；
- 同一次 execution 内的重复查询返回该 `@` 应用创建的同一对象。

当前反射 API 只查询类型目标。其他目标的 binary/runtime metadata 已保存在同一 Core metadata 模型中。

完整声明、策略与 FunctionTarget 生命周期见 [Annotation 规范](/spec/annotations)。
