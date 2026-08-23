# 关键字

关键字在所有源码上下文中保留，不能作为普通标识符使用。

## 声明

`package`、`import`、`as`、`class`、`value`、`interface`、`enum`、`annotation`、`extends`、`implements`、`public`、`private`

## 控制流

`if`、`else`、`for`、`switch`、`case`、`break`、`continue`、`return`、`try`、`catch`、`finally`、`throw`

## 值与类型操作

`true`、`false`、`null`、`this`、`super`、`is`、`as`、`reflect`

基本类型名如 `Integer`、`Boolean` 和 `Void` 由语言预声明，也不能重新定义。

## 兼容性

新增关键字可能破坏旧源码，因此稳定版本应尽量使用上下文关键字，或通过新的语言版本启用。当前规范不提供反引号转义关键字作为标识符的语法。

大小写敏感：`class` 是关键字，`Class` 可以是类型名。关键字只能使用 ASCII 字符，避免视觉相似字符影响审查。

`Module` 不是关键字。它是仅由 `module.norm` 使用的编译期内置类型名，字段标签 `name`、`version` 和 `exports` 也都是普通标识符。

