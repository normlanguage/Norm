# 语言哲学

## 显式优于隐式

Norm 希望重要语义边界直接出现在代码中：

- `T?`：值可能为 null。
- `Ref&lt;T&gt;`：存在共享 identity。
- `reflect`：进入元编程/拦截边界。
- `break value`：控制表达式正在产生值。

## 业务建模优先

泛型用于安全复用，不鼓励类型级编程。字符串模板是一等能力，字符串 `+` 被禁止。Annotation 可以有行为，但必须通过 `reflect` 明确标注。

## 默认不共享

```norm
User a = User(name = "Alice")
User b = a
b.name = "Bob"
```

`a.name` 仍为 `Alice`。需要共享：

```norm
Ref&lt;User&gt; shared = a.ref()
shared.name = "Bob"
```

## 平台不是规范

Norm 初期基于 GraalVM/Truffle，但语言语义、类型系统、反射模型和 IR 不依赖 JVM 类型模型。

