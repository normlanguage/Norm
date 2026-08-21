# 接口

接口描述一组行为，不保存实例字段。Norm 使用名义类型关系：class 必须显式声明实现某个接口。

## 声明接口

```norm
interface Measurable {
    double measure()
}
```

接口方法只描述调用约定。实现类型提供具体行为：

```norm
class Circle implements Measurable {
    double radius

    double measure() {
        return 3.14159 * radius * radius
    }
}
```

## 通过接口使用值

```norm
double total(Measurable first, Measurable second) {
    return first.measure() + second.measure()
}
```

调用者只依赖 `Measurable` 公开的行为，不需要知道具体 class。

## 名义关系

下面的类型虽然也有 `measure()` 方法，但它没有声明 `implements Measurable`：

```norm
class Timer {
    double measure() {
        return 0.0
    }
}
```

因此 `Timer` 不会自动成为 `Measurable`。这条规则让大型代码库中的类型关系可以通过声明直接查找。

## 接口与共享无关

接口只描述行为，不决定值是否共享。class 通过接口变量使用时仍遵循默认值语义；只有 `Ref<T>` 才引入共享 identity。

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

下一章：[Enum 与 Switch](/language/enum-switch)。

