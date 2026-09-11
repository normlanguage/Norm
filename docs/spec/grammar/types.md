# 类型语法

Norm 使用名义类型系统和类型前置声明。类型写在变量、字段、参数和返回值的名称之前。

```norm
Integer count = 3
String title = "Grammar"
List<String> names = List<>()
```

## 类型形式

```text
Type := NamedType
      | NamedType "<" TypeArgumentList ">"
      | Type "?"
      | FunctionType

TypeArgument := Type | "?"
```

当前类型形式包括命名类型、参数化类型、nullable 类型和函数类型。数组、列表与映射是标准库泛型类型，不是特殊的类型语法。

## Nullable

`T` 不包含 `null`，`T?` 才包含。nullable 标记只作用于紧邻的完整类型：

```norm
List<String>? optionalList
List<String?> listWithOptionalItems
```

这两个类型不同：前者允许列表本身为空，后者允许列表元素为空。`ref<T>` 与 nullable 的组合由引用类型规范定义。

`T?` 在类型替换后规范化。如果 T 已经是 nullable 类型，结果仍为一层 nullable。Void 不能声明为 nullable。

后缀非空断言 `expression!!` 去掉表达式结果类型的 nullable 标记，并在运行时检查其值。操作数只求值一次；若为 null，抛出可由 `catch Exception` 捕获的异常。断言不改变原字段的声明类型，也不保证该字段的后续读取非空。

```norm
Long id = todo.id!!
```

`!!` 与成员访问、调用和索引一样按后缀顺序结合，优先于前缀运算符；例如 `!checked!!` 先断言再取反。可以对已非空的值使用断言；无可用非空类型的 null 字面量及 Void 表达式不能使用断言。

## 泛型参数

类型标注中的泛型类型必须提供全部必填参数；只有声明了默认类型的尾部参数可以省略。Norm 没有 raw type，省略默认参数会在语义分析时展开为完整类型。构造调用省略类型实参或使用 `<>` 时，从实参和期望类型推导，再使用声明的默认类型补齐未确定的参数；无法确定必填参数时报告错误。

```norm
Map<String, Integer> counts
Map counts // 编译错误
```

参数化类型不变。`?` 是存在类型投影，表示“这个实参存在，但当前代码不知道它”：

```norm
Class<?> type
Field<User, ?> field
Function<?> function
```

投影值只能使用不依赖被隐藏实参的成员。例如 `Function<?>` 可查询名称和参数，但不能被直接调用；调用需要精确的 `Function<R(P...)>`。完整边界见[泛型不变性](/spec/generic-variance)。

## 函数类型

```norm
Integer operation(Integer value)
```

函数类型包含返回类型和参数列表。参数名用于局部可读性，不参与类型相等；返回类型和每个参数类型参与兼容性判断。
