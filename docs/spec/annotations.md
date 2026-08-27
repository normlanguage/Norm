# Annotation 规范

Annotation 是声明上的常量元数据。它不参与普通表达式求值，也不改变被标记声明的语义。

## 声明

```norm
annotation Label targets(type, field) retention(runtime) {
    String text
    String? replacement = null
}
```

`targets` 和 `retention` 必须显式声明。目标可选 `package`、`type`、`field`、`constructor`、`function`、`parameter` 和 `local`；保留级别可选 `source`、`binary` 和 `runtime`。

字段没有默认值时为必填参数。0.12 的字段类型限于 `Boolean`、`CodePoint`、`Integer`、`Long`、`Float`、`Double`、`String` 及其 nullable 形式；参数和默认值只接受对应字面量。

## 使用

```norm
@Label(text: "legacy parser", replacement: "parse")
Integer parseLegacy(String text) {
    return parse(text: text)
}
```

Annotation 参数只允许命名传递。参数名必须存在且不可重复，必填参数必须提供，值必须是可赋给字段类型的编译期常量。同一 annotation 类型在一个目标上只能出现一次。

## 保留

- `source` 仅存在于源码语义模型；
- `binary` 写入 Core metadata；
- `runtime` 写入 Core metadata，并可由 `Type<T>.annotation<A>()` 查询。

Schema 是 canonical Core definition；应用属于 `CoreArtifact.metadata`，声明目标使用 `DefinitionOccurrenceId`，字段、参数和局部目标在 occurrence 内使用稳定序号。

Annotation 实例只能由 metadata 读取产生，不能在普通代码中直接构造。Annotation 不允许类型参数、继承、interface、构造器或方法，字段必须为公开不可变字段。
