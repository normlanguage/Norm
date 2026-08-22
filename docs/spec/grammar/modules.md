# Module 描述语法

```text
ModuleFile := ModuleExpression ";"?
ModuleExpression := "Module" "(" ModuleField ("," ModuleField)* ")"
ModuleField := "name" ":" StringLiteral
             | "version" ":" IntegerLiteral
             | "exports" ":" ExportList
ExportList := "[" (StringLiteral ("," StringLiteral)*)? "]"
```

三个字段顺序任意，但必须各出现一次。`name` 和每个 export 都是由点分隔的有效标识符；`version` 必须为正整数；export 不能重复。文件在 `Module` 表达式后不得包含其他内容。

`Module(...)` 使用普通调用表达式和命名参数语法。模块加载阶段将该表达式解释为编译期内置 `Module` 值。路径映射与可见性见[模块系统](/spec/module-system)。
