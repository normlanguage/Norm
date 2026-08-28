# 04 Class、Value 与 Interface

不是所有数据都具有同一种语义：实体保留身份，数据按值工作，能力通过名义接口表达。

<<< ../../norm/tests/docs/tour/04_data_model.norm{norm}

输出：

```text
true
point
1
```

| 构造 | 表达什么 | 状态 | 组合方式 |
| --- | --- | --- | --- |
| `class` | 具有身份的实体 | 字段可变 | 单类继承、接口实现 |
| `value` | 由内容定义的数据 | 构造后不可重新赋值 | 接口实现 |
| `enum` | 有限且可携带数据的选择 | variant 数据 | 模式匹配 |
| `interface` | 能力和替换契约 | 无实例字段 | 多接口继承、默认方法 |

## Class 保留身份

class 赋值、传参和返回都保留同一个对象身份。需要新顶层身份时显式调用 `copy()`；class 字段指向的其他对象仍然共享。

## Value 表达内容

value 可以声明泛型参数、方法并实现 interface。字段在构造后不能重新赋值，也不参与类继承。赋值、传参和返回遵循 value 规则，相等与 hash 递归使用字段语义。

## Interface 表达能力

类型只有显式写出 `implements` 才满足 interface；拥有同名方法不会自动形成关系。interface 可以继承多个 interface，并提供默认方法。

构造、继承和初始化规则见[对象模型](/spec/object-model)、[Class 声明](/spec/grammar/classes)和[Value 声明](/spec/grammar/values)。

上一章：[函数与调用](/learn/functions)。下一章：[数据 Enum 与 Switch](/learn/enum-switch)。
