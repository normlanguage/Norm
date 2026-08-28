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
List<Field<Point>> fields = point.fields()
String firstField = fields[0].name()
ReflectedValue value = fields[0].read(receiver: Point(x: 1, y: 2))
```

- `reflect<T>()` 返回 reified `Type<T>`；
- `Type<T>.name()` 返回稳定的 Norm 类型显示名；
- `Type<T>.annotation<A>()` 只接受 Annotation 类型，读取类型目标上的 `RuntimeRetention` 实例；不存在时返回 `null`；
- `Type<T>.fields()` 按稳定 ordinal 返回字段；`Field<T>` 提供名称、索引、字段类型名、runtime Annotation 与受控读取；
- `Field<T>.read()` 返回携带精确字段类型的 `ReflectedValue`，不会通过字符串调用 getter；
- 同一次 execution 内的重复查询返回该 `@` 应用创建的同一对象。

结构反射与序列化运行时读取同一份 Core field metadata，字段值按 ordinal 访问。Function 与 Parameter metadata 也保存在 Core 中，其公共结构查询入口将在对应调用模型稳定后开放。

完整声明、策略与拦截生命周期见 [Annotation 规范](/spec/annotations)。
