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

`Function<R(P...)>` 不声明参数名称，其函数值使用位置参数调用，例如 `combine(first, second)`；不接受编译器内部生成的参数标签。命名 callable 参数保留声明中的参数名称和标签规则。

## Lambda 与闭包

普通函数、方法、getter、接口默认方法与 Lambda 的末尾表达式可省略 `return`。末尾 `if / else` 的分支遵循同一规则；所有正常完成路径必须提供与返回类型兼容的值。提前退出使用显式 `return`。`Void` 函数不产生结果；省略返回类型的 fluent 方法仍返回接收者。

```norm
Integer doubled(Integer value) { value * 2 }
Integer choose(Boolean first) {
  if first { 1 } else { 2 }
}
```

```norm
var doubled = (Integer value) { value * 2 }
Function<Integer(Integer)> tripled = (value) { value * 3 }
var quadrupled = Integer(Integer value) { value * 4 }
```

Lambda 的结果类型使用期望函数类型和末尾表达式提供的类型约束；有明确返回类型时，控制流路径也按普通函数规则检查。

Lambda 可以捕获外层局部、参数和 `this`。被捕获的局部与参数必须 effectively-final；class 捕获保持对象身份，其他值遵循普通赋值语义。

## 尾随 Lambda

显式类型实参也可直接接尾随 Lambda，例如 `submit<Integer> { 42 }`、`runner.run<List<String>> { ["Norm"] }`；无需添加空参数括号。只有尾随 Lambda 实参时，格式化器统一省略空参数括号。控制结构中的花括号边界仍遵循本节规则。

调用可以把一个 Lambda 放在参数括号之后；仅传入该 Lambda 时也可以省略调用括号。无参形式为 `{ body }`，有参形式为 `{ name, other in body }`，参数类型由期望函数类型推导。`in` 仅在该参数头中作为分隔符。

API 使用命名回调签名时，省略参数头的尾随 Lambda 自动获得契约中的参数名称和类型：

```norm
Void submit(Void completed(String title)) { completed("任务") }
Void main() { submit { printLine(title) } }
```

这些参数属于 Lambda 自己的作用域，可以遮蔽外层同名变量；嵌套闭包遵循普通捕获规则。显式 `{ other in ... }` 使用调用者的参数名，显式 `() { ... }` 始终表示零参数。只声明 `Function<Void(String)>` 的回调没有参数名契约，调用者必须显式声明参数。编辑器提供隐式参数的类型和补全；需要改名时使用显式参数头。

```norm
button(text: "添加") { store.submit() }
runApp(title: "待办清单") { scope in
  todoScreen(scope: scope, path: path)
}
```

尾随 Lambda 绑定到声明中最后一个函数类型参数。其后的配置参数仍遵循普通默认参数和标签规则；其他必填参数不能省略，同一参数不能重复传入。类型推导、重载选择、闭包捕获和求值顺序复用普通 Lambda 实参规则。

当首参数以后的参数均有默认值或已由尾随 Lambda 提供时，首参数可省略标签，例如 `Button("全部") { reload(null) }`。存在其他必填参数时继续使用标签。

`Type() { ... }` 和 `Type(value) { ... }` 按构造调用解析。带返回类型的 Lambda 形式需要非空参数列表，且首参数显式声明类型，例如 `Integer(Integer value) { value * 2 }`；无参 Lambda 使用 `() { ... }`，返回类型由期望类型或末尾表达式推导。

`if` 条件、`for` 条件或迭代源及 `switch` 输入之后的顶层花括号属于控制结构。需要在这些位置调用尾随 Lambda 时，对该调用加括号，例如 `if (test() { true }) { ... }`。

## 函数与方法引用

需要把内容块中的多个表达式累计为一个结果时，使用[结果构建器](/spec/grammar/result-builders)。

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
