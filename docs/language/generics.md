# 泛型

泛型让一个类型或函数在保留静态类型信息的前提下处理多种类型。Norm 把泛型用于安全复用，而不是类型级编程。

## 泛型类型

```norm
class Box<T> {
    T value
}
```

使用泛型类型时必须提供类型参数：

```norm
Box<int> count = Box<int>(value: 3)
Box<String> label = Box<String>(value: "ready")
```

Norm 禁止 raw type，因此不能只写 `Box`。

## 泛型函数

```norm
T first<T>(List<T> values) {
    return values[0]
}
```

函数体只能使用对所有可能 `T` 都成立的操作。需要额外行为时，添加类型约束。

## 类型约束

```norm
T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(right) >= 0 {
        return left
    }
    return right
}
```

`extends Comparable<T>` 要求类型参数显式满足这个接口关系。

## 型变

Norm 使用 Java 风格的通配符表达只读生产者和写入消费者：

```norm
List<? extends Shape> shapes
List<? super Circle> destinations
```

`? extends Shape` 表示某个未知的 `Shape` 子类型；`? super Circle` 表示某个能够接收 `Circle` 的父类型。具体赋值和调用规则见[泛型型变规范](/spec/generic-variance)。

## 运行时泛型信息

Norm 不擦除实际类型参数。

```norm
List<String>.class
List<int>.class
List<String>.class.T == String.class
```

因此 `List<String>` 与 `List<int>` 在运行时具有不同且可查询的类型描述。反射和通用库不需要通过外部 token 重新传递已经存在的类型信息。

## 泛型的边界

Norm 不把泛型设计成独立的类型级计算语言：

- 不允许 raw type；
- 不依靠隐式转换修补不兼容类型；
- 不鼓励把业务逻辑编码进复杂类型表达式；
- 不允许泛型隐藏共享或 nullable 语义。

下一章：[错误处理](/language/errors)。

