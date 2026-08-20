# Enum 与 Switch

Norm enum 参考 Rust，可携带数据：

```norm
enum Result&lt;T, E&gt; {
    Ok(T value)
    Err(E error)
}
```

模式匹配统一使用 `switch`，不增加 `match`：

```norm
String message = switch result {
    case Ok(User user) {
        break "Hello ${user.name}"
    }
    case Err(UserError error) {
        break error.message
    }
}
```

`switch` 可匹配 enum、class 和 value；封闭 enum 做穷尽性检查。

类型判断和转换使用：

```norm
if person is Employee {
    print(person.department)
}
Employee employee = person as Employee
```

