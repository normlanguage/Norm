# 函数高级规则

本页补充重载、函数值、匿名函数和方法引用的静态规则。基础声明语法见[函数](/language/functions)。

## 重载解析

候选函数按名称、可见性、参数名和参数类型筛选。调用必须得到唯一最佳候选；编译器不会通过猜测数值收窄或 nullable 转换消除歧义。

```norm
String format(Integer value) { return "${value}" }
String format(Decimal value) { return "${value}" }

String text = format(value: 3)
```

只改变返回类型不能形成重载，因为调用点可能不提供足够信息。

## 函数值

```norm
Integer transform(Integer operation(Integer value), Integer input) {
    return operation(input)
}
```

函数值不携带隐藏接收者。实例方法引用 `counter.incrementBy` 会显式绑定该接收者；普通匿名函数不能任意捕获外层局部变量。

```norm
Integer doubled = transform(
    operation: Integer(Integer value) { return value * 2 },
    input: 4
)
```

## 递归与泛型

函数可以直接或间接递归。泛型函数在函数名后声明类型参数：

```norm
T identity<T>(T value) {
    return value
}
```

类型推断只使用调用实参和明确的期望类型，不分析函数体来推断公开签名。

