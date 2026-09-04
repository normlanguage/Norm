# Annotation 规范

Annotation 是具有稳定 identity 的特殊 class。它可以声明字段、显式构造器和普通方法，可以实现 interface，但不能声明类型参数或继承 class。

## 策略 interface

Annotation 必须实现至少一个目标 interface，并且只实现一种保留策略。标准定义见 [`std.annotation`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/annotation/protocols.norm)：

- 目标：`PackageTarget`、`TypeTarget`、`FieldTarget`、`ConstructorTarget`、`FunctionTarget`、`ParameterTarget`、`LocalTarget`；
- 拦截：`FunctionInterceptor`、`FieldInterceptor<T>`、`ParameterInterceptor<T>`；
- 保留：`SourceRetention`、`BinaryRetention`、`RuntimeRetention`。

普通 class 和 value 不能实现这些策略 interface。自定义策略 interface 可以继承标准策略，目标和保留策略按名义 interface conformance 推导。

```norm
import std.annotation.TypeTarget
import std.annotation.RuntimeRetention

annotation Label implements TypeTarget, RuntimeRetention {
  String text

  String display() {
    return text
  }
}
```

## 构造与应用

`@Label(text: "point")` 定义一次 Annotation 对象构造。参数必须完整且为可赋值的编译期值；当声明包含名为 `value` 的参数时，第一个实参可以省略标签，例如 `@Route("/hello")`。其他实参必须命名。可用值包括标量常量和类型化声明引用 `T.class`、`name.function`、`Owner.name.function`、`Owner.name.field`。显式构造器存在时使用它的参数，否则使用字段生成的构造参数。Annotation 也能在普通表达式中直接构造，字段可变。

声明引用在 Core metadata 中保留目标 identity，不保存声明名字符串。目标丢失或重载不唯一时编译失败。

每次 execution 中，一个 `@` 应用在首次被函数拦截或 runtime reflection 观察时构造一次；两条路径读取同一实例，不同 execution 的实例隔离。未被运行时观察的应用不执行构造器。同一 Annotation 类型不能重复应用于同一目标。

目标 interface 只决定 Annotation 能标在哪里，不引入执行行为。拦截 interface 继承对应目标，并定义生命周期。

## FunctionInterceptor

实现 `FunctionInterceptor` 的 Annotation 可以覆盖 `before`、`around`、`after`。被标记的具体函数或方法在定义侧自动执行该生命周期；直接调用、动态分派和函数引用共享同一入口。

多个 Annotation 按源码顺序嵌套。每层的 `before` 正常完成后才进入该层，再由 `around` 决定是否以及何时调用 `proceed()`；进入后，无论 `around` 或函数体正常返回还是抛出异常，都会执行 `after`。`before` 自身抛出时不执行本层 `after`，`after` 抛出的异常替代原完成态。`FunctionCompletion.succeeded()` 表示该层 `around` 是否正常返回。`proceed()` 至多调用一次。

带 `ref` 参数或返回类型的 callable 不能使用 `FunctionInterceptor`，interface requirement 也不能直接拦截。

## ParameterInterceptor

实现 `ParameterInterceptor<T>` 的 Annotation 可以覆盖 `before(ParameterContext, T)` 和 `after(ParameterContext, FunctionCompletion)`。`T` 必须与被标参数的声明类型精确一致，`ref<T>` 参数和 interface requirement 参数不能使用参数生命周期。

`before` 通过 `context.parameter()` 取得 `Parameter<T>` 声明引用，并可以继续查询 `name()`、`type()` 和 `function()`。它可以校验参数、抛出异常，或返回新的 `T` 写入 callee 参数槽。输入和返回值都经过 value-copy 边界。`after` 不接收参数值或引用，只观察该层是否正常完成，不能替换参数绑定。

多个参数生命周期按参数序号和 Annotation 源码顺序进入，按完整反序退出。与 `FunctionInterceptor` 同时存在时，参数生命周期位于 `around` 的 `proceed()` 内部；`around` 不调用 `proceed()` 时不会执行参数生命周期。直接调用、构造、动态分派和函数引用共用定义侧入口。

## FieldInterceptor

实现 `FieldInterceptor<T>` 的 Annotation 可以覆盖 `before(FieldContext, T)` 和 `after(FieldContext, FunctionCompletion)`。`T` 必须与字段声明类型精确一致。

字段初始化、普通赋值和通过 `ref<T>` 的间接赋值共用同一生命周期。`before` 通过 `context.field()` 取得 `Field<Owner, T>` 声明引用，可校验、抛出异常或返回新的 `T` 写入存储；`after` 在存储完成后观察完成态，不能替换已写入的值。多个 Annotation 按源码顺序进入、反序退出；继承字段保留声明侧行为。

`FunctionContext.function()` 返回 `Function<?>`。拦截器与日志、校验和文档工具因此共用同一套声明引用，不传递名称和 ordinal 副本。

## Document

`std.annotation.Document` 是 BinaryRetention 的结构化文档 Annotation，可用于 package、类型、字段、构造器、函数、参数和局部声明。`description` 保存正文，`types`、`functions`、`fields` 分别保存有序的类型、函数和字段声明引用；三个 nullable 列表均可省略。

```norm
@Document(
  description: "按标识查询用户。",
  types: [User.class],
  functions: [findUser.function],
  fields: [User.id.field]
)
```

Annotation 元数据可以使用标量、声明引用及由这些值递归组成的 `List` 字面量。`List` 表示有序声明元数据；`Array` 不是 Annotation 元数据类型。非 nullable 参数必须显式提供，省略 nullable 参数等价于提供 `null`。完整声明以 [`std.annotation`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/annotation/protocols.norm) 为准。

编译器可直接把保留在语义模型中的 `Document` 与声明、类型和源码位置导出为模块 API 树；命令、文件映射和前端组件见 [API 文档导出](/tooling/api-documentation)。

## 保留与 Core

- `SourceRetention` 不生成通用 metadata；FunctionInterceptor、ParameterInterceptor 与 FieldInterceptor 行为仍分别编码在 callable、parameter 与 field Core 中；
- `BinaryRetention` 生成 Core metadata；
- `RuntimeRetention` 生成 Core metadata，并允许反射读取。

Annotation 声明使用统一的 aggregate Core 定义；应用数据位于 `CoreArtifact.metadata`，行为位于对应声明的 interceptor 列表。声明目标使用 `DefinitionOccurrenceId`，不会因相同 Core body 合并不同源码应用。

可复用的参数与字段约束见 [Validation API](/stdlib/validation-api)。
