# 06 Null 与类型推断

缺失直接进入类型；推断利用上下文减少重复，但不会退化成动态类型或无依据猜测。

<<< ../../norm/tests/docs/tour/06_nullability_inference.norm{norm}

输出：

```text
seven
missing
```

## 可空性

`String` 始终非空，`String?` 才能保存 `null`。集合本身和集合元素的可空性分别表达：

```norm
List<String>? optionalNames = null
List<String?> names = ["Norm", null]
```

`?.` 在接收者为空时停止成员访问，`??` 在左侧为空时才计算回退表达式。显式的 null 检查可以在控制流内收窄类型。

## 期望类型

集合字面量、泛型调用和 diamond 构造器会同时使用实参和期望结果：

```norm
Array<Integer> fixed = [1, 2, 3]
List<Integer> dynamic = [1, 2, 3]
List<Pair<Integer, String>> values = List<>()
```

裸泛型类型不合法，类型参数当前保持 invariant。`null`、空集合和无约束的 `List<>()` 不能在没有上下文时独立确定类型；推断失败直接产生诊断。

算法与边界见[类型推断](/spec/type-inference)和[泛型参考](/spec/grammar/generics)。

上一章：[数据 Enum 与 Switch](/learn/enum-switch)。下一章：[集合与迭代](/learn/collections)。
