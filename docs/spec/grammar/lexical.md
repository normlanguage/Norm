# 词法规则

Norm 源文件使用 UTF-8。编译器将文件解码为 Unicode 文本后再建立源码位置。

## 空白与换行

空格、制表符和换行分隔 token。换行通常不结束表达式；语法结构和运算符决定表达式是否完整。行首解引用的语句边界见 [`ref<T>` 引用语法](/spec/grammar/references)。行尾分号可以省略，首版规范不鼓励同一行写多条语句。

## 标识符

标识符以 Unicode 字母或下划线开始，后续可包含 Unicode 字母、十进制数字或下划线。关键字不能作为标识符。规范化形式使用 NFC，两个规范化后相同的名称视为重复声明。

```norm
String displayName
Integer retry_count
```

public API 建议使用 ASCII 标识符以提高工具与生态兼容性。编译器应警告容易混淆的跨脚本字符。

## 数字与字符串 token

数字 token 包含整数、小数点、指数和数字分隔下划线；具体类型由字面量与期望类型决定。String 使用双引号和反斜杠转义。当前 Lexer 不接受字符串插值，也不把 `//` 或 `/* */` 识别为注释；交付状态见 [Status](/status)。

## 源码位置

`SourceSpan` 保存文件和 UTF-16 code unit 的半开范围，行列位置使用同一单位并与 LSP 对齐。需要面向原始文件字节的协议时，应从 UTF-8 输入单独建立 byte range，不能把它与 `SourceSpan` 混用。
