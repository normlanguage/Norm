# 接口

接口描述一组行为，不保存实例字段。它是 Norm 唯一的名义行为抽象机制；标准库所称 protocol 只是普通接口。类型必须显式声明实现关系。

## 声明接口

```norm
interface Measurable {
    Double measure()
}
```

接口方法可以只描述调用约定，也可以提供默认实现。实现类型可以直接使用默认实现或提供自己的实现：

```norm
class Circle implements Measurable {
    Double radius

    Double measure() {
        return 3.14159 * radius * radius
    }
}
```

## 通过接口使用值

```norm
Double total(Measurable first, Measurable second) {
    return first.measure() + second.measure()
}
```

调用者只依赖 `Measurable` 公开的行为，不需要知道具体 class。

## 名义关系

下面的类型虽然也有 `measure()` 方法，但它没有声明 `implements Measurable`：

```norm
class Timer {
    Double measure() {
        return 0.0
    }
}
```

因此 `Timer` 不会自动成为 `Measurable`。这条规则让大型代码库中的类型关系可以通过声明直接查找。

## 多继承与动态分派

接口可以通过 `extends` 继承多个接口，但继承图不能成环。通过接口调用方法时，运行时优先选择具体名义类型的实现，否则调用唯一适用的默认实现。

## 接口与共享无关

接口只描述行为，不改变底层数据类别。class 通过接口变量使用时仍保留对象身份，value 仍遵循 value 语义。

## 接口与泛型

接口经常作为泛型约束：

```norm
T larger<T extends Comparable<T>>(T left, T right) {
    if left.compareTo(right) >= 0 {
        return left
    }
    return right
}
```

这里的约束表示 `T` 必须显式实现 `Comparable<T>`。更多内容见[泛型](/language/generics)。

`Iterable<T>`、`Iterator<T>`、`Sized`、`Comparable<T>`、`Equatable<T>` 与 `Hashable` 都是 `std.core` 中的普通接口。遍历式 `for` 使用 Iterable；这些接口不会重载语言操作符，也不会替换 Map 与 Set 的语言内建 equality/hash。

下一章：[Enum 与 Switch](/language/enum-switch)。
