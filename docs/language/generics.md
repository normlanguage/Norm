# 泛型

泛型让一个类型或函数在保留静态类型信息的前提下处理多种类型。Norm 把泛型用于安全复用，而不是类型级编程。

## 当前边界

当前实现支持无 bounds 的泛型 class、泛型函数与实例方法、参数化核心集合、嵌套 nullable 类型实参、基于实参和期望返回类型的调用推断，以及运行时类型参数保留。泛型默认不变且禁止 raw type。

`extends` bounds、interface 约束、通配符型变、泛型数据 enum 与反射 API 属于后续严格扩展，不改变已有泛型的类型 identity 和可赋值规则。

## 泛型类型

```norm
class Box<T> {
    T value
}
```

使用泛型类型时必须提供类型参数：

```norm
Box<Integer> count = Box<Integer>(value: 3)
Box<String?> label = Box<String?>(value: null)
```

Norm 禁止 raw type，因此不能只写 `Box`。

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
        return Pair<T, U>(first: first, second: second)
    }
}

Pair<String, Integer> value = Values<String>().pair<Integer>(first: "Norm", second: 4)
```

## 不变性

不同类型实参形成不同的不变类型。`List<String>` 不能赋给 `List<String?>`；允许 null 元素时必须在集合类型中明确声明。

## 泛型的边界

Norm 不把泛型设计成独立的类型级计算语言：

- 不允许 raw type；
- 不依靠隐式转换修补不兼容类型；
- 不鼓励把业务逻辑编码进复杂类型表达式；
- 不允许泛型隐藏共享或 nullable 语义。

下一章：[错误处理](/language/errors)。
