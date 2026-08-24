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

构造器位于 enum 类型的命名空间中，并使用命名实参：

```norm
Token token = Token.Number(value: 1.5)
Result<Integer, Error> result = Result<Integer, Error>.Ok(value: 1)
```

泛型 enum 也可以在实参与期望类型得到唯一解时省略类型实参，例如 `Result<Integer, Error> result = Result.Ok(value: 1)`。

## 使用 Switch 解构

```norm
String describe(Token token) {
    return switch token {
        case Number(Double value) {
            break "number"
        }
        case Name(String text) {
            break "name: " + text
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

`case Number(Double value)` 同时检查 variant 并把内部数据绑定到 `value`。variant 的数据位置仍可使用 variant、类型化绑定、`_`、字面量或 `null`，因此模式可以递归嵌套。

## 穷尽性

每个 `switch` 都必须穷尽。封闭 enum 必须覆盖全部 variant；无法有限枚举的值域、开放类型或 nullable 类型使用 `_` 覆盖其余值。新增 variant 后，遗漏它的 switch 会产生编译错误。

这条规则尤其适合状态机和解析器，因为状态集合发生变化时，受影响的分支会直接暴露出来。

被匹配表达式只求值一次，首个匹配 case 独占执行，case 之间没有 fallthrough。表达式 case 的正常完成路径必须以 `break value` 产生结果。

## Result 也是普通 Enum

`Result<T, E>` 与 Unit 定义在 [`std.core`](/stdlib/overview)，不需要特殊的语言级传播运算符。Result 使用与其他 enum 相同的构造、匹配和穷尽规则；没有业务值的成功结果使用 `Result<Unit, E>`。

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
