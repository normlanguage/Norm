# 字面量

## 数字

```norm
Integer count = 42
Long population = 8_100_000_000
Double ratio = 0.125
```

下划线只能位于数字之间，用于分组且不影响值。无上下文时，整数在 Integer 范围内使用 Integer，否则使用 Long；小数字面量默认使用 Double。具体数值目标类型优先：`Long value = 7`、`Float ratio = 0.125` 直接按目标类型物化。解析器保留精确十进制文本，类型求解完成前不进行浮点舍入。

## 字符串

```norm
String name = "Norm"
String line = "first\nsecond"
```

单引号表示一个 `CodePoint`。内容必须解码为恰好一个 Unicode code point：

```norm
CodePoint letter = 'a'
CodePoint emoji = '😀'
CodePoint newline = '\n'
```

字符串使用双引号并支持标准转义。

## 布尔与 Null

`true` 和 `false` 的类型是 Boolean。`null` 只能出现在已有 nullable 期望类型的位置，不能单独推断为任意类型。运行时使用 guest null value 表示该值，不把宿主语言 null 暴露为 Norm 值。

## 集合

`[1, 2, 3]` 是序列字面量。expected type 为 `Array<T>` 或 `List<T>` 时直接构造对应容器；为 `Iterable<T>` 时把元素约束投影到默认的 `Array<T>`；无容器上下文时也默认为 `Array<T>`。它不会先构造 Array 再转换成 List。多个具体数字叶类型的最小公共类型是 `Number`。

```norm
Array<Integer> array = [1, 2, 3]
List<Integer> list = [1, 2, 3]
List<Number> numbers = [1, 2.5, 3]
```

空 `[]` 没有元素约束时必须由赋值、参数或返回位置提供完整类型。

