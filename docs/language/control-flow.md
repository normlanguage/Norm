# 控制流

Norm 主要使用 `if`、`for`、`switch`。

```norm
if user.active {
    process(user)
}
```

作为表达式时必须显式 `break value`，且所有退出路径完备：

```norm
String state = if user.active {
    break "active"
} else {
    break "inactive"
}
```

`for` 是 foreach 形状，没有 C 风格 for 和 while：

```norm
for User user : users {
    process(user)
}
```

Norm 不提供 `0..10`，范围由普通类型表达，例如 `Array(start = 0, end = 10)`。

for expression 用 `else` 表示正常耗尽且没有 value-break 的路径：

```norm
User admin = for User user : users {
    if user.admin {
        break user
    }
} else {
    break defaultAdmin
}
```

语言不会为缺失路径隐式补 null。

