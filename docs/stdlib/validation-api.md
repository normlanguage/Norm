# Validation API

验证库把不可信输入转换成满足约束的领域值。验证失败是正常结果，使用结构化错误返回，而不是依赖异常或字段 annotation。

```norm
value ValidationError {
    String path
    String code
    String message
}

Result<EmailAddress, List<ValidationError>> validateEmail(String input) {
    // 显式检查并构造领域值
}
```

## 组合规则

`Validator<T>` 可以通过普通函数组合：

```norm
Validator<String> username = Validators.string()
    .trimmed()
    .length(minimum: 3, maximum: 32)
    .matches(pattern: usernamePattern)
```

转换和验证是两个步骤：解析失败使用 `InvalidFormat`，解析成功但违反领域规则使用具体 code。默认收集全部独立错误；需要遇错即停时必须显式选择。

## 路径与本地化

错误 path 使用结构化字段段和索引段构成，展示层再渲染为 `user.addresses[0].city`。核心错误携带稳定 code 和参数，不把本地化后的最终文案作为机器契约。

## 边界

验证不会自动修改领域对象，也不会绕过构造器不变量。Web 表单、JSON 和配置模块可以适配同一 Validator，但传输层字段名到领域类型的映射必须显式可查。

