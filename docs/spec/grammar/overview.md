# 语法参考总览

本目录描述 Norm 源码的词法 token 和语法结构。当前使用接近 EBNF 的记法；首个编译器实现需要把全部产生式固化为机器可测试 grammar。

## 记法

```text
"token"      固定关键字或符号
Name         另一条产生式
A?           可选
A*           零次或多次
A+           一次或多次
A | B        二选一
```

## 源文件

```text
SourceFile := PackageDeclaration? Import* Declaration*

ModuleFile := ModuleExpression ";"?
ModuleExpression := "Module" "(" ModuleField ("," ModuleField)* ")"
ModuleField := "name" ":" StringLiteral
             | "version" ":" IntegerLiteral
             | "exports" ":" "[" (StringLiteral ("," StringLiteral)*)? "]"
```

package 位于文件开头，import 位于其他声明之前。没有 package 的文件是单文件脚本。普通源文件顶层允许类型、函数和编译期常量，不允许任意执行语句。源码根的 `module.norm` 是模块描述文件，只包含一个 `Module` 对象表达式。

## 声明

```text
Declaration := ClassDeclaration
             | ValueDeclaration
             | InterfaceDeclaration
             | EnumDeclaration
             | AnnotationDeclaration
             | FunctionDeclaration
```

Norm 使用类型前置：`String name`、`Integer parse(String text)`。generic 参数写在声明名后，nullable 标记写在完整类型后。

## 表达式与语句

字面量、名称、成员访问、调用、索引、运算和控制表达式产生值。变量声明、赋值、return、throw 等组成语句。if、for 和 switch 在值位置通过 `break value` 显式产生结果。

## 相关章节

- [词法规则](/spec/grammar/lexical)
- [声明](/spec/grammar/declarations)
- [类型](/spec/grammar/types)
- [表达式](/spec/grammar/expressions)
- [语句](/spec/grammar/statements)
- [模块描述](/spec/grammar/modules)
- [运算符优先级](/spec/grammar/operators-precedence)
