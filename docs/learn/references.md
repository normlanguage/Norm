# 10 引用

`ref<T>` 表达 value 存储位置的身份，让可变别名显式出现并被限制在词法生命周期内。

<<< ../../norm/tests/docs/tour/10_references.norm{norm}

输出：

```text
2
```

| 形式 | 含义 |
| --- | --- |
| `ref<T>` | `T` 的存储位置引用 |
| `&location` | 取得可写位置的地址 |
| `*reference` | 读取位置中的 value |
| `*reference = value` | 替换位置中的 value |

## 可寻址位置

可写局部变量、参数和 class 的 value 字段可以取地址。字面量、临时表达式、调用结果、value 字段、容器元素和 null-safe 访问结果不能取地址。

## 类型与生命周期边界

- `T` 只能是 value 类型；
- ref 不能嵌套，也不能 nullable；
- ref 只用于局部变量和 callable 参数；
- ref 不能作为返回类型、字段、enum payload、泛型实参或函数类型的一部分；
- ref 不能被 Lambda 捕获或越过被引用位置的作用域。

Class 已经具有对象身份，不使用 `ref<Class>` 表达共享。完整静态规则见 [`ref<T>` Reference](/spec/grammar/references)。

上一章：[错误与异常](/learn/errors)。下一章：[Annotation](/learn/annotations)。
