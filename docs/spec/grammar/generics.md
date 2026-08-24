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
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

多个约束的具体连接语法尚未定稿；规范示例目前每个参数只展示一个 extends 上界。

bound 必须是 interface 类型。实际类型参数通过显式声明的 `implements` 或 interface `extends` 关系满足 bound；拥有相同成员不构成满足关系。约束内的调用通过 interface 动态分派。

## 使用

类型位置必须写全部实参，raw type 非法。函数与实例方法调用可以在约束得到唯一解时省略显式类型实参，否则分别写 `function<Type>(...)` 与 `receiver.method<Type>(...)`。

实际类型参数会进入 Core IR 和运行时类型环境。参数化类型不变；菱形构造器只省略表达式中可由约束唯一求解的实参。
