# 类型语法

Norm 使用名义类型系统和类型前置声明。类型写在变量、字段、参数和返回值的名称之前。

```norm
int count = 3
String title = "Grammar"
List<String> names = List<String>()
```

## 类型形式

```text
Type := NamedType
      | NamedType "<" TypeArgumentList ">"
      | Type "?"
      | FunctionType
```

当前类型形式包括命名类型、参数化类型、nullable 类型和函数类型。数组、列表与映射是标准库泛型类型，不是特殊的类型语法。

## Nullable

`T` 不包含 `null`，`T?` 才包含。nullable 标记只作用于紧邻的完整类型：

```norm
List<String>? optionalList
List<String?> listWithOptionalItems
```

这两个类型不同：前者允许列表本身为空，后者允许列表元素为空。`ref<T>` 与 nullable 的组合由引用类型规范定义。

## 泛型参数

使用泛型类型必须写出全部参数；Norm 没有 raw type。

```norm
Map<String, int> counts
Map counts // 编译错误
```

通配符只出现在类型实参位置：`? extends T` 表示只读生产者，`? super T` 表示可写消费者。完整规则见[泛型型变](/spec/generic-variance)。

## 函数类型

```norm
int operation(int value)
```

函数类型包含返回类型和参数列表。参数名用于局部可读性，不参与类型相等；返回类型和每个参数类型参与兼容性判断。

