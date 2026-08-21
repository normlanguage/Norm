# 类型推断

Norm 的公开声明保持类型前置和显式。类型推断主要服务于泛型调用、控制表达式结果与局部模式绑定，而不是省略 API 签名。

## 推断位置

```norm
List<String> names = emptyList()
String first = identity(value = "Norm")
```

编译器可以根据实参、赋值目标和泛型约束推断函数的类型参数。字段、参数和返回类型不能省略。

## 约束求解

对每个类型参数，编译器收集：

1. 实参类型产生的下界或等式；
2. 赋值目标产生的期望类型；
3. `extends` 声明产生的上界；
4. nullable 与型变规则产生的附加约束。

求解必须得到唯一、满足全部上界的类型。无法确定时要求调用者显式提供类型参数。

```norm
List<String> names = emptyList<String>()
```

## 不执行的推断

- 不根据函数体补全公开签名；
- 不通过隐式数值收窄寻找候选；
- 不把 `null` 单独推断为任意 nullable 类型；
- 不跨模块猜测未声明的结构类型关系。

形式化约束与算法见[泛型推断](/spec/formal/generic-inference)。

