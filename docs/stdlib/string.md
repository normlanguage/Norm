# String

String 是不可变 Unicode 文本值。构造后内容不变，切片和替换返回新 String；实现可以共享底层存储但不能泄露可变视图。

```norm
String language = "Norm"
String message = "Hello, " + language
```

## 长度与索引

文本存在字节、Unicode code point 和 grapheme cluster 等不同单位。API 使用 `byteSize()`、`codePointSize()` 和 `graphemeSize()` 明确区分，不提供含糊的 `size()` 或 `length`，也不承诺 `text[index]` 等于用户看到的第 index 个字符。

`CodePoint` 是独立的 Unicode 标量类型，字符字面量只允许包含一个 code point：

```norm
CodePoint letter = 'N'
CodePoint emoji = '😀'
Integer scalar = emoji.scalarValue()
Boolean digit = emoji.isDecimalDigit()
Boolean asciiDigit = letter.isAsciiDigit()
```

`CodePoint` 提供 `isDecimalDigit()`、`isAsciiDigit()`、`asciiDigitValue()`、`isLetter()`、`isWhitespace()`、`isUppercase()` 和 `isLowercase()`。`isAsciiDigit()` 只接受 `0` 到 `9`，`asciiDigitValue()` 返回对应整数并在其他输入上产生 `INVALID_ARGUMENT`。大小写映射属于 String，因为一个 code point 的映射结果可能包含多个 code point。

需要随机访问文本时，先显式选择单位：

```norm
Array<CodePoint> points = text.codePoints()
Array<String> graphemes = text.graphemes()
String part = text.sliceCodePoints(start: 1, end: 4)
String visiblePart = text.sliceGraphemes(start: 1, end: 4)
```

`codePoints()` 和 `graphemes()` 返回独立的 value 数组。修改数组不会改变原始 String。`sliceCodePoints` 使用左闭右开的 code point 范围。

## 状态与比较

```norm
Boolean empty = text.isEmpty()
Integer order = text.compareCodePoints(right: other)
Boolean headerMatches = text.equalsIgnoreCaseAscii(other: "content-type")
```

`compareCodePoints` 返回 `-1`、`0` 或 `1`。`equalsIgnoreCaseAscii` 只折叠 ASCII 大小写，适用于协议标识符，不受系统 locale 影响。

## 搜索与切分

`contains`、`startsWith`、`endsWith` 和 `split` 按精确文本匹配。

```norm
Boolean present = text.contains(value: "Norm")
Boolean prefix = text.startsWith(prefix: "No")
Boolean suffix = text.endsWith(suffix: "rm")
Array<String> components = path.split(separator: "/")
```

`split` 保留首尾和相邻分隔符产生的空片段。

## 替换与空白

```norm
String all = text.replace(target: "old", replacement: "new")
String first = text.replaceFirst(target: "old", replacement: "new")
String clean = text.trim()
String left = text.trimStart()
String right = text.trimEnd()
```

替换采用字面量匹配，空 target 属于无效参数。trim 系列按照 Unicode whitespace 判断，不读取系统 locale。

## 大小写与规范化

```norm
String lower = text.toLowercase()
String upper = text.toUppercase()

import std.text.Normalization
import std.text.isNormalized
import std.text.normalize

String normalized = normalize(value: text, form: Normalization.Nfc)
Boolean canonical = isNormalized(value: normalized, form: Normalization.Nfc)
```

无 locale 参数的大小写转换使用稳定的 Unicode locale-independent 规则。`Normalization` 提供 `Nfc`、`Nfd`、`Nfkc` 和 `Nfkd`。

## 查找与解析

普通缺失位置使用 nullable Integer 表达。文本编解码和需要携带错误原因的解析使用后续的 Bytes 与 Result API。

解析数字、UUID 和时间由目标类型的 parse API 完成，String 不提供隐式跨类型转换。

## 文本构造函数

`std.text` 已提供 `repeat`、`join` 和 `fromCodePoints`，实现在 `std/text/builders.norm`：

```norm
import std.text.join
import std.text.repeat

String line = repeat(value: "-", count: 8)
String text = join(values: names, separator: ", ")
String rebuilt = fromCodePoints(values: points)
```
