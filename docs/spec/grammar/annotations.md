# Annotation 声明

annotation 是附加到声明上的静态元数据。它不能改变语法含义、注入控制流或替代普通函数调用。

```norm
annotation Deprecated {
    String message
    String? replacement = null
}

@Deprecated(message: "use parse", replacement: "parse")
Integer parseLegacy(String text) {
    return parse(text: text)
}
```

## 参数规则

annotation 参数必须是编译期常量：基本字面量、String、enum variant、类型描述或这些值的不可变集合。必填参数没有默认值，可选参数必须声明默认值。

## 目标与保留

每个 annotation 类型声明允许的目标和保留级别。当前草案区分 source、binary 与 runtime。runtime annotation 可由反射读取，但读取必须通过显式 API。

## 边界

- annotation 的求值不能执行用户代码；
- 未识别 annotation 是否报错由其命名空间和工具链扩展规则决定；
- Web 路由、验证和依赖关系优先使用显式注册，不应把 annotation 变成隐藏框架语言；
- 编译器专用 annotation 必须进入标准命名空间并有规范定义。

