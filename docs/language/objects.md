# Class、Value 与 Ref

## class

class 默认递归值复制，可以继承，也可以显式 `.ref()`：

```norm
class User {
    String name
}

User b = a
Ref<User> shared = a.ref()
```

实现可以使用 COW、结构共享、逃逸分析与 copy elision，但语言语义仍是独立值。

## value

```norm
value Money {
    decimal amount
    Currency currency
}
```

value 字段不可原地修改，但变量本身可整体重新赋值；value 没有 identity，不支持 `.ref()`。

## Getter / Setter

class 字段自动具有隐藏 accessor。需要改变行为时可声明对应方法：

```norm
class User {
    String name

    void setName(String value) {
        field = value.trim()
    }
}
```

调用仍写 `user.name = "Alice"`。

## 继承

`Person p = Employee(...)` 保留完整 Employee 动态类型，不发生 object slicing。父构造必须显式 `super(...)`。
