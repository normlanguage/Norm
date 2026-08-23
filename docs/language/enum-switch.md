# Enum 与 Switch

## 简单 Enum

```norm
enum Direction {
    North,
    East,
    South,
    West
}
```

每个 variant 都是 `Direction` 的一个可能值。

```norm
Direction direction = Direction.North
```

枚举值可以赋值、传参、返回，并使用 `==` 和 `!=` 比较。

## 携带数据的 Variant

```norm
enum Token {
    Number(Double value),
    Name(String text),
    Plus,
    End
}
```

`Number` 和 `Name` 携带数据，`Plus` 和 `End` 不携带数据。一个 enum 可以在同一类型中精确表达这些不同情况。

## 使用 Switch 解构

```norm
String describe(Token token) {
    return switch token {
        case Number(Double value) {
            break "number ${value}"
        }
        case Name(String text) {
            break "name ${text}"
        }
        case Plus {
            break "plus"
        }
        case End {
            break "end"
        }
    }
}
```

`case Number(Double value)` 同时检查 variant 并把内部数据绑定到 `value`。

## 穷尽性

对封闭 enum 使用 `switch` 时，编译器检查所有 variant 是否得到处理。新增 variant 后，遗漏它的 switch 会产生编译错误。

这条规则尤其适合状态机和解析器，因为状态集合发生变化时，受影响的分支会直接暴露出来。

## Result 也是普通 Enum

```norm
enum Result<T, E> {
    Ok(T value),
    Err(E error)
}
```

`Result<T, E>` 不需要特殊的语言级传播运算符。它使用与其他 enum 相同的构造、匹配和穷尽规则。

## 类型判断与转换

运行时类型检查使用 `is`：

```norm
if shape is Circle {
    printLine("circle")
}
```

显式类型转换使用 `as`：

```norm
Circle circle = shape as Circle
```

`as` 的失败行为和控制流收窄规则属于正式类型系统的一部分，手册不替代对应规范。

下一章：[泛型](/language/generics)。

