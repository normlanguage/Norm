# 对象模型规范

Norm 的复合数据分为没有身份的 value、具有身份的 class、行为契约 interface 和封闭数据类型 enum。赋值、复制与相等的完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

## Class

class 可以拥有字段、构造器和方法，并且可以继承一个 class、实现多个 interface。实例可变且具有稳定身份；赋值、传参和返回共享同一实例。`copy()` 创建新的顶层身份，并逐字段执行普通赋值。

把子 class 赋给父类型或 interface 变量时保留动态类型，不发生 object slicing。可覆盖方法按动态类型分派。

## Value

value 表示没有 identity 的数据。字段在构造完成后不可原地修改；赋值、传参和返回产生逻辑独立值。相等与 hash 递归使用全部字段。value 可以实现 interface，但不参与 class 继承。

## Interface

interface 是唯一的名义行为抽象，只声明契约且不保存实例字段。实现与继承关系必须显式声明，interface 可以多继承，成员形状相同不会自动建立关系。interface 方法没有默认实现；通过 interface 调用时按具体名义类型动态分派，且不改变值原有的 value 或 identity 类别。

## Enum

enum 是封闭的代数数据类型。variant 可以不携带数据，也可以拥有不同字段；泛型 enum 沿用普通泛型构造与推断规则。所有 switch 都执行穷尽检查，variant 数据可由递归模式解构。enum 属于 value。

## `ref<T>`

`ref<T>` 为 value 存储位置提供 identity。复制 ref 保留同一位置；它不接受 class，因为 class 已经具有对象身份。

## 表示自由

规范不固定字段布局、对象头、垃圾回收方式或 value 的复制策略。运行时只需保持可观察的身份、结构相等、动态分派和修改行为。
