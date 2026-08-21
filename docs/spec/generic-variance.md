# 泛型型变规范

Norm 的泛型默认不变，并使用调用点通配符表达安全的读取或写入关系。

```norm
List<Circle> circles
List<Shape> shapes

shapes = circles // 编译错误：List<T> 不变
```

## 生产者：extends

`? extends Shape` 表示某个未知的 Shape 子类型。可以安全读取为 Shape，但不能写入具体 Shape。

```norm
List<? extends Shape> source = circles
Shape first = source[0]
source.add(value: Circle()) // 编译错误
```

## 消费者：super

`? super Circle` 表示某个可以接收 Circle 的未知父类型容器。可以写入 Circle，但读取结果只能视为通配符的未知上界。

```norm
List<? super Circle> target = shapes
target.add(value: Circle())
```

## 约束

- 通配符只出现在类型实参位置，不能作为声明名称使用。
- 同一通配符不能同时写 `extends` 与 `super`。
- `ref<T>` 不提供型变：可写存储位置必须保持不变，否则会破坏写入安全。
- nullable 是独立类型构造，不会由型变规则自动添加或移除。

API 设计可遵循“生产者 extends，消费者 super”，但当一个参数既读又写时应接受精确的 `T`。

