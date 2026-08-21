# Class、Value 与 Ref

Norm 把“数据是否具有身份、赋值后是否共享”作为类型模型的一部分。理解 `class`、`value` 和 `Ref<T>` 的区别，是理解 Norm 的关键。

## Class：带行为的独立值

`class` 用于同时包含状态和行为的类型。

```norm
class Counter {
    int value

    void increment() {
        value = value + 1
    }
}
```

创建 class 实例不使用 `new`：

```norm
Counter counter = Counter(value = 0)
counter.increment()
```

与 Java 不同，class 变量赋值默认不会产生隐藏共享。

```norm
Counter first = Counter(value = 0)
Counter second = first

second.increment()
```

执行后，`first.value` 仍为 `0`，`second.value` 为 `1`。语言语义要求它们是独立值。

运行时可以使用写时复制、结构共享或复制消除来优化实现，但优化不能改变可观察行为。

## Value：纯数据值

`value` 用于没有 identity 的不可变数据。

```norm
value Point {
    int x
    int y
}
```

value 的字段不能原地修改：

```norm
Point point = Point(x = 2, y = 4)
point.x = 8
```

第二行非法。变量本身可以整体指向另一个值：

```norm
point = Point(x = 8, y = point.y)
```

value 具有值相等、哈希能力和复制语义，适合坐标、区间、颜色或其他数学意义上的值。

## Ref：显式共享 identity

当多个位置必须观察并修改同一对象时，使用 `Ref<T>`。

```norm
Counter original = Counter(value = 0)
Ref<Counter> shared = original.ref()

shared.increment()
```

`Ref<Counter>` 直接告诉读者：这里存在共享 identity。共享不再是赋值操作的隐含副作用。

`Ref<T>` 本身永不 nullable。以下类型非法：

```norm
Ref<Counter>?
Ref<Counter?>
```

如果程序需要表达“可能没有共享对象”，应通过一个显式 enum 建模，而不是把 null 与 identity 混合。

## 选择哪一种

| 需求 | 使用 |
| --- | --- |
| 不可变纯数据 | `value` |
| 有行为、赋值后应彼此独立 | `class` |
| 多处必须共享同一可变对象 | `Ref<class>` |
| 只描述一组行为 | `interface` |

## 字段访问

class 字段自动拥有访问入口。普通读写直接使用字段语法：

```norm
counter.value = 10
print("${counter.value}")
```

需要验证或转换时，可以声明对应 setter。字段内部使用 `field` 表示实际存储：

```norm
class Percentage {
    int value

    void setValue(int next) {
        field = clamp(value = next, minimum = 0, maximum = 100)
    }
}
```

调用点仍写成 `percentage.value = 120`，但赋值行为由 setter 明确定义。

## 继承

class 支持单继承。父构造调用必须显式写出 `super(...)`。把子类型赋给父类型变量时，完整动态类型会保留，不发生 object slicing。

继承只表达类型与行为关系，不会改变默认值语义。共享仍然只能通过 `Ref<T>` 引入。

下一章：[函数](/language/functions)。

