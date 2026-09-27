# 泛型

泛型使一个类型或函数可用于多个类型，同时保留静态类型信息。

## 当前边界

当前实现支持泛型 class、数据 enum、函数和实例方法，名义 class 与 interface 约束、引用前序类型参数的约束、参数化核心集合、嵌套 nullable 类型实参、从参数和期望返回类型推断，以及运行时类型实参。泛型类型保持不变性，不允许裸类型。使用位置型变与反射 API 属于后续扩展。

## 泛型类型

```norm
class Box<T> {
    T value
}

Box<Integer> count = Box<>(value: 3)
Box<String?> label = Box<>(value: null)
```

禁止裸类型：没有类型实参的 `Box` 无效。

## 泛型函数

```norm
T identity<T>(T value) {
    return value
}

Integer count = identity(3)
String? label = identity(null)
```

期望的 `String?` 返回类型提供了 `null` 自身无法提供的类型信息。

实例方法使用相同的推断和显式类型实参语法。运行时类型环境中，泛型 class 的类型实参位于方法类型实参之前：

```norm
class Values<T> {
    Pair<T, U> pair<U>(T first, U second) {
        return Pair<>(first: first, second: second)
    }
}

Pair<String, Integer> value = Values<String>().pair<Integer>(first: "Norm", second: 4)
```

## 不变性

不同类型实参产生不同的不变类型。`List<String>` 不可赋给 `List<String?>`；元素可空性必须在集合边界显式声明。

## 名义约束

```norm
T larger<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

约束可命名一个非空的 class、interface 或前序类型参数。Class 约束通过类继承满足；interface 约束通过显式 `implements` 或 interface `extends` 满足。类型参数约束在替换外层类型实参后检查，例如 `U extends T`。仅有同名成员并不足够。通过 class 约束调用成员和绑定方法值时仍保留虚方法分派；通过 interface 约束调用时使用接口分派。

返回[手册导论](/zh/language/overview)。
