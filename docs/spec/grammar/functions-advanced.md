# 函数高级规则

本页补充重载、函数值、Lambda、闭包和方法引用的静态规则。入门路径见[函数与调用](/learn/functions)。

## 重载解析

候选函数按名称与可见性收集，并依次按参数数量、参数标签、参数类型和泛型推断结果筛选。调用必须得到唯一目标；返回类型不参与重载 identity，也不用于打破歧义。

```norm
String format(Integer value) { return "integer" }
String format(String value) { return value }

String text = format(value: 3)
```

## 函数类型与函数值

函数类型完整记录返回类型和参数类型：

```norm
Function<Integer(Integer)> transform
Function<Boolean(String)> predicate
Function<Void()> action
```

参数声明可以使用等价的 callable 形式：

```norm
R mapValue<T, R>(R transform(T value), T value) {
  return transform(value)
}
```

`var` 推导出的仍是完整函数类型，不存在 raw `Function`。

## Lambda 与闭包

```norm
var doubled = (Integer value) { value * 2 }
Function<Integer(Integer)> tripled = (value) { value * 3 }
var quadrupled = Integer(Integer value) { value * 4 }
```

Lambda 的最后一条表达式是结果；包含其他控制流时使用显式 `return`。类型推导同时使用期望函数类型和 Lambda 自身提供的类型约束。

Lambda 可以捕获外层局部、参数和 `this`。被捕获的局部与参数必须 effectively-final；class 捕获保持对象身份，其他值遵循普通赋值语义。

## 函数与方法引用

```norm
Function<Integer(Integer)> first = doubled
Function<Integer(Integer)> second = counter.add
Integer add(Integer amount) = counter.add
Function<Integer(Counter, Integer)> unbound = Counter.add.function
Function<?> declaration = Counter.add.function
```

顶层函数可直接转换为期望函数类型，`receiver.method` 创建绑定接收者的函数值。`Owner.method.function` 是未绑定声明引用，其精确签名把 receiver 作为第一个参数，调用时仍按 receiver 的动态类型分派。顶层声明使用 `name.function`。

重载引用在精确的期望 `Function<R(P...)>` 下必须唯一确定。`Function<?>` 只保留声明 identity 和可查询 metadata，不可直接调用。函数值可以存入字段、传参和返回，并以 `operation(value)` 调用。声明引用的统一规则见[声明引用与反射](/spec/declaration-references)。

## 递归与泛型

函数可以直接或间接递归。泛型函数在函数名后声明类型参数：

```norm
T identity<T>(T value) {
  return value
}
```

类型推断只使用调用实参和明确的期望类型，不分析具名函数体来推断公开签名。
