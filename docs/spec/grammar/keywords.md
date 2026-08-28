# 关键字

关键字在所有源码上下文中保留，不能作为普通标识符使用。

## 声明

`package`、`import`、`as`、`class`、`interface`、`enum`、`extends`、`implements`、`public`、`private`

## 控制流

`if`、`else`、`for`、`switch`、`case`、`break`、`continue`、`return`、`try`、`catch`、`finally`、`throw`

## 值与类型操作

`true`、`false`、`null`、`this`、`super`、`is`、`as`

基本类型名如 `Integer`、`Boolean` 和 `Void` 由语言预声明，也不能重新定义。

## 兼容性

新增关键字可能破坏旧源码，因此稳定版本应尽量使用上下文关键字，或通过新的语言版本启用。当前规范不提供反引号转义关键字作为标识符的语法。

大小写敏感：`class` 是关键字，`Class` 可以是类型名。关键字只能使用 ASCII 字符，避免视觉相似字符影响审查。

`value` 是上下文关键字，只在顶层的 `value TypeName` 声明头中具有特殊含义；字段、参数、局部变量和函数仍可命名为 `value`。

`annotation` 是上下文关键字，只在顶层 Annotation 声明头中具有特殊含义。目标与保留策略是 `std.annotation` 中的普通 interface 名称。

`extension` 是上下文关键字，只在顶层 extension function 声明头中具有特殊含义。

`reflect` 是预声明的 generic 函数名，可以被词法作用域中的普通声明遮蔽。

`Module` 和 `module` 都不是关键字。前者是 bootstrap interface，后者的模块入口与 bootstrap 工厂都是普通函数；参数标签 `name`、`version`、`exports` 和 `dependencies` 都是普通标识符。

