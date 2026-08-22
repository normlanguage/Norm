# String

String 是不可变 Unicode 文本值。构造后内容不变，切片和替换返回新 String；实现可以共享底层存储但不能泄露可变视图。

```norm
String language = "Norm"
String message = "Hello, ${language}"
```

## 长度与索引

文本存在字节、Unicode code point 和 grapheme cluster 等不同单位。API 使用 `byteSize()`、`codePointSize()` 和 `graphemeSize()` 明确区分，不提供含糊的 `size()` 或 `length`，也不承诺 `text[index]` 等于用户看到的第 index 个字符。

## 搜索与切分

`contains`、`startsWith`、`find` 和 `split` 默认按精确 code point 比较。大小写无关、locale 规则和 Unicode normalization 必须通过显式选项请求。

## 编码

```norm
Bytes bytes = text.encode(encoding: TextEncoding.Utf8)
Result<String, DecodeError> decoded = String.decode(
    bytes: bytes,
    encoding: TextEncoding.Utf8
)
```

无效字节默认返回错误；替换无效序列需要显式选择 loss-tolerant 模式。

解析数字、UUID 和时间由目标类型的 parse API 完成，String 不提供隐式跨类型转换。

## 0.2 文本构造函数

`std.text` 已提供 `repeat` 和 `join`，实现在 `std/text/builders.norm`：

```norm
import std.text.join
import std.text.repeat

String line = repeat(value: "-", count: 8)
String text = join(values: names, separator: ", ")
```
