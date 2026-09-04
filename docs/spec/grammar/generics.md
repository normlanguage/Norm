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

每个类型参数当前只接受一个 `extends` 名义上界，不提供多个上界的连接语法。上界可以是 class、interface 或前面已声明的类型参数；`U extends T` 在替换外层类型实参后验证。

## 默认类型

类型参数可以声明默认类型。默认参数必须连续位于参数列表末尾，只能引用前面声明的类型参数，并且必须满足自己的上界。

```norm
enum Result<T, E = String> {
    Ok(T value, String msg = ""),
    Err(E error, String msg = "")
}
```

`Result<Integer>` 等价于 `Result<Integer, String>`。显式传入 `Result<Integer, Failure>` 会覆盖默认类型。默认类型在语义分析时展开，Core IR、NAR 公开 ABI 和运行时类型仍保存完整的两个实际类型参数；它不是 raw type。

class bound 通过 class 继承关系满足；interface bound 通过显式声明的 `implements` 或 interface `extends` 关系满足。拥有相同成员不构成满足关系。约束内的 class 方法调用与绑定方法值保留虚方法分派，interface 方法调用通过 interface 动态分派。

## 使用

类型位置必须提供全部必填实参，raw type 非法；仅能省略声明了默认类型的尾部实参。函数与实例方法调用可以在约束得到唯一解时省略显式类型实参，否则分别写 `function<Type>(...)` 与 `receiver.method<Type>(...)`。

实际类型参数会进入 Core IR 和运行时类型环境。参数化类型不变；菱形构造器只省略表达式中可由约束唯一求解的实参。
