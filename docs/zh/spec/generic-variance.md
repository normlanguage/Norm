# 泛型不变性

Norm 的参数化类型保持不变。`List<Circle>` 与 `List<Shape>` 是不同类型，二者不可直接赋值；nullable 也不会改变这一规则。

通用读写能力由显式 interface 和泛型约束表达。当前类型语法不包含使用位置通配符。
