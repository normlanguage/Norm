# 函数

Norm 允许顶层函数：

```norm
String display(User user) {
    return user.name
}
```

多个参数默认使用命名调用：

```norm
transfer(from = source, to = destination, amount = money)
```

构造不使用 `new`：

```norm
User user = User(name = "Alice", age = 20)
```

## 函数类型与匿名函数

```norm
void apply(User transform(User user), User input) {
    User result = transform(input)
}
```

匿名函数就是去掉名字后的普通函数：

```norm
User(User user) {
    return User(name = user.name.trim())
}
```

不允许捕获任意局部变量，因此没有传统 closure。绑定方法引用允许：

```norm
process(handler = formatter.format)
```

## 重载与覆盖

参数名或类型至少一个不同即可形成重载，但任何导致调用歧义的声明都非法。public 实例方法默认可覆盖；private 方法不参与覆盖；没有 `final/open/virtual/override`。

