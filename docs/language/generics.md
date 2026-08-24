# 泛型

泛型让一个类型或函数在保留静态类型信息的前提下处理多种类型。Norm 把泛型用于安全复用，而不是类型级编程。

## 当前边界

当前实现支持泛型 class、数据 enum、函数与实例方法，支持 interface bound、参数化核心集合、嵌套 nullable 类型实参、基于实参和期望返回类型的调用推断，以及运行时类型参数保留。泛型默认不变且禁止 raw type。通配符型变与反射 API 属于后续扩展。

## 泛型类型

```norm
class Box<T> {
    T value
}
```

使用泛型类型时必须提供类型参数：

```norm
Box<Integer> count = Box<>(value: 3)
Box<String?> label = Box<>(value: null)
```

Norm 禁止 raw type，因此不能只写 `Box`。

构造表达式的 `<>` 表示由 expected type 和构造参数共同推断实参：

```norm
Stack<Integer> indices = Stack<>()
var inferred = Stack<Integer>()
```

diamond 只出现在构造表达式中，不能写在声明类型上。`var` 仅用于带初始化器的局部变量；`null`、空序列和无约束 diamond 不能单独决定其类型。

## 泛型函数

```norm
T identity<T>(T value) {
    return value
}

Integer count = identity(3)
String? label = identity(null)
```

`null` 本身不能决定 `T`，上例由赋值目标 `String?` 提供期望类型。函数体只能使用对所有可能 `T` 都成立的操作。

实例方法使用同一套推断与显式类型实参语法。泛型 class 的类型参数排在方法类型参数之前进入运行时类型环境：

```norm
class Values<T> {
    Pair<T, U> pair<U>(T first, U second) {
        return Pair<>(first: first, second: second)
    }
}

Pair<String, Integer> value = Values<String>().pair<Integer>(first: "Norm", second: 4)
```

## 不变性

不同类型实参形成不同的不变类型。`List<String>` 不能赋给 `List<String?>`；允许 null 元素时必须在集合类型中明确声明。

## Interface Bounds

```norm
T larger<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(other: right) >= 0 { return left }
    return right
}
```

bound 只引用 interface。实际类型必须通过 `implements` 或 interface `extends` 的显式名义关系满足约束；同名成员不会结构化匹配。

## 泛型的边界

Norm 不把泛型设计成独立的类型级计算语言：

- 不允许 raw type；
- 不依靠隐式转换修补不兼容类型；
- 不鼓励把业务逻辑编码进复杂类型表达式；
- 不允许泛型隐藏共享或 nullable 语义。

下一章：[错误处理](/language/errors)。
