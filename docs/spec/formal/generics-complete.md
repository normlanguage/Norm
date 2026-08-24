# 泛型系统形式化

Norm 泛型在编译期提供静态复用，在运行时保留实际类型参数。它不是独立的类型级编程语言。

## 声明

```norm
class Box<T> {
    T value
}

T maximum<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

类型变量在声明体和成员签名内可见。使用该变量的操作必须对所有满足 bound 的实际类型成立。

## 实例化

`G<A1...An>` 要求实参数量与声明一致，每个 Ai 满足对应 bound。raw `G` 非法。不同实际参数默认产生不相容的不变类型。

## 类型推断

函数调用从实参、期望返回类型和 declared bounds 产生约束。求解必须唯一且只使用安全转换；失败时显式写类型实参。

## 运行时表示

每个参数化类型描述至少包含：

- 泛型声明 identity；
- 有序实际类型参数；
- nullable 信息；
- bound 与成员替换结果。

这些信息直接进入 Core IR 与运行时类型环境，无需额外 Class token。

## 二进制与缓存

实现可以共享泛型机器码、单态化或采用混合策略，但 runtime type descriptor 必须完整。编译缓存键包含泛型声明版本和实际参数，不能因代码共享错误复用不兼容布局。

## 限制

当前不提供高阶类型、类型函数、条件类型、使用位置通配符或隐式 typeclass 搜索。
