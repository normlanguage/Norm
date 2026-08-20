# 异常与 Result

Norm 保留 Java 风格 `try/catch/finally/throw`，异常用于非正常执行状态。

业务层可预期失败使用普通泛型 enum `Result&lt;T,E&gt;`。

```norm
switch result {
    case Ok(User user) { ... }
    case Err(LoginError error) { ... }
}
```

Norm 不提供 Result 的 `?` 一类语言级传播语法，避免形成第二套隐藏控制流。

