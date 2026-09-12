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

## 块调用链

刚结束尾随 Lambda 的调用，可以在同一行继续一个仅带尾随 Lambda 的普通成员调用：

```norm
async {
  repository.findByCompleted(completed)
} then {
  todos = result
}
```

这等价于 `async { ... }.then { ... }`，不是一次调用的两个回调实参。各段左结合，后一段接收前一段的返回值；整条表达式的类型是最后一次调用的返回类型。该规则适用于普通成员与 extension，不限于 Task，也不把 `then`、`error` 定义为关键字。

只有同时满足以下条件才能省略点号：

| 边界 | 要求 |
| --- | --- |
| 前件 | 当前表达式是调用，最近消费的真实 `}` 结束该调用自己的尾随 Lambda。 |
| 后继 | 紧接的两个 token 是 `IDENTIFIER` 和 `{`；后继只传一个尾随 Lambda。 |
| 行与分隔 | 前件 `}`、成员名称、后继 `{` 同行，中间没有其他 token。LF、CRLF 与 CR 均按源码行索引处理。 |
| 控制结构 | 当前表达式深度允许尾随 Lambda；条件和迭代源中的调用链仍须加括号。 |

`produce{}map{}` 同样可识别；格式化统一输出 `} map {`。闭包体可以跨行，连接头不能跨行。后继其余参数只有在既有默认实参规则允许时才能省略。

```norm
produce { work() } map { item in transform(item) } finish { save(result) }
produce { work() }; independent { consume() }
```

在 `}` 与名称之间或名称与 `{` 之间换行，都不构成省点号链；在语句列表中仍可表示独立调用。同一行的两个独立尾随闭包调用必须使用分号分隔，或将后一个调用另起一行。字符串内容不参与连接判断，插值表达式沿用普通表达式规则；本规则不增加注释语法。

`task map { ... }`、`produce() map { ... }`、`(produce { ... }) map { ... }` 不构成块调用链。不能越过 `)`、`]`、后续属性访问或控制结构的 `}` 去连接更早的闭包。后继需要显式类型实参、普通实参或安全访问时，继续使用 `.map<R> { ... }`、`.map(option: value) { ... }` 或 `?.map { ... }`。

连接属于普通 postfix 调用；例如 `left + produce { ... } map { ... }` 中 `map` 作用于 `produce` 的结果，而不是整个加法。形成链之后只按成员调用解析目标；缺少成员、重载歧义或类型错误不会回退为同名顶层函数。

每段回调独立拥有参数作用域，隐式参数名称来自该段 API 的命名回调签名。捕获、默认实参、泛型、Void/Unit、nullable、class identity、值复制与 `ref<T>` 规则均与显式点号版本一致。接收者只求值一次；不增加等待、线程切换、自动解引用、安全访问或嵌套 Task 展开，也不改变 `return`、`break`、`throw` 的目标。Task 运行契约见[并发 API](/stdlib/concurrency)。

格式化只在完整调用树满足这些结构条件时规范化为无点号形式，不合并独立语句或结果构建器元素；带语法错误的源码不做猜测式改写。连接头不因行宽限制而折行。

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
