# 11 Annotation

Annotation 是具有类型、目标和保留策略的声明对象；元数据和可选行为仍使用普通名义接口约束。

<<< ../../norm/tests/docs/tour/11_annotations.norm{norm}

输出：

```text
coordinate
```

## 目标与保留

Annotation 必须实现至少一个目标 interface，并选择一种保留策略。`TypeTarget`、`FieldTarget`、`FunctionTarget`、`ParameterTarget` 等决定允许的应用位置；`SourceRetention`、`BinaryRetention` 和 `RuntimeRetention` 决定保留边界。

应用参数必须命名，并使用兼容的编译期标量、声明引用或 `List` 常量。非 nullable 参数必须提供；nullable 参数可以省略。同一 Annotation 类型不能重复应用到同一目标。

## 类型化行为

高级 Annotation 可以实现：

- `FunctionInterceptor`；
- `ParameterInterceptor<T>`；
- `FieldInterceptor<T>`。

生命周期使用 `before`、`around` 和 `after`。参数与字段拦截器的类型参数必须与实际声明类型精确一致。直接调用、动态分派和函数引用共享定义侧行为入口。

反射通过 `T.class`、`Class<T>` 和强类型声明引用读取 runtime metadata，不使用 JVM reflection 或字符串 getter。内建 `@Document` 以 `description` 保存正文，并通过 `types`、`functions`、`fields` 列表关联声明。完整生命周期见 [Annotation 规范](/spec/annotations)，引用规则见[声明引用与反射](/spec/declaration-references)。

上一章：[引用](/learn/references)。下一章：[Package 与 Module](/learn/packages-modules)。
