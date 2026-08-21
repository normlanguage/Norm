# 泛型语法

类型参数写在类型名或函数名之后。每个参数是当前声明中的类型名称。

```norm
class Box<T> {
    T value
}

T first<T>(List<T> values) {
    return values[0]
}
```

## 约束

```norm
T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other = right) >= 0 { return left }
    return right
}
```

多个约束的具体连接语法尚未定稿；规范示例目前每个参数只展示一个 extends 上界。

## 使用

类型位置必须写全部实参，raw type 非法。函数调用可以在约束得到唯一解时省略显式类型实参，否则写 `function<Type>(...)`。

实际类型参数在运行时保留，`List<String>.class` 与 `List<int>.class` 不相同。型变不写在声明上，而由 `? extends T` 与 `? super T` 在使用位置表达。

