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
String configuration = "\${bbs.greeting:Hello}"
String greeting = "Hello, ${name}!"
```

单引号表示一个 `CodePoint`。内容必须解码为恰好一个 Unicode code point：

```norm
CodePoint letter = 'a'
CodePoint emoji = '😀'
CodePoint newline = '\n'
```

字符串使用双引号并支持标准转义。`${expression}` 对表达式求值并通过其 `toString()` 契约产生文本；求值顺序从左到右。`\${` 保留字面 `${`，用于配置占位符等文本。

三引号 `"""` 包围多行字符串，保留内容中的换行和缩进，转义和插值规则与普通字符串一致。内容中的一个或两个连续双引号无需转义；三个连续双引号结束字符串。格式化保留多行字符串的原始内容。

```norm
String query = """
  select todo from Todo todo
  order by todo.id
"""
```

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

序列字面量内支持条件元素、循环元素和展开；它们适用于 Array 与 List，不限于 UI。

```norm
List<Integer> source = [1, 2, 3]
List<Integer> values = [
  0,
  if (source.size() > 0) 10 else 20,
  for (value : source) if (value != 2) value * 2,
  ...source
]
```

`if (condition)` 根据条件贡献一个元素或零个元素，`else` 可省略；`for (value : source)` 为每次迭代贡献其内部元素，也支持显式元素类型和索引变量。`...source` 按顺序展开 Iterable 的元素。三者可以嵌套；条件和迭代源在到达对应位置时求值一次，未选择的分支不执行。循环变量仅在内部元素中可见，每轮闭包捕获独立的元素值。

集合控制结构的条件和迭代头使用括号。需要把普通 `if` 表达式作为单个元素时，可以使用 `(if condition { first } else { second })`。空迭代源需要声明元素类型；不能从没有类型信息的 `[]` 推断循环变量。

