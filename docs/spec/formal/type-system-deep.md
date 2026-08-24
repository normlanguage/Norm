# 类型系统深入规则

本页说明类型组合处容易产生歧义的边界：nullable、泛型、函数和动态类型。

## Nullable 组合

nullable 标记作用于完整类型：`List<String>?` 与 `List<String?>` 不同。重复 nullable `T??` 不形成新类型，应规范化为 `T?` 或直接诊断冗余。

## Reified 泛型

`List<String>` 与 `List<Integer>` 在 Core IR 和运行时类型环境中保留不同的实参，不依赖擦除后的外部 token。

## 函数类型

```norm
Function<String(Point)> formatter
```

函数类型由返回类型和参数类型序列决定，不包含参数名。Lambda 使用期望类型与自身约束双向推导，可以捕获 effectively-final 的外层局部、参数和 `this`；绑定方法引用显式携带接收者。

## 动态分派与复制

父类型或 interface 变量保存完整动态类型。复制 class 值后，两个副本分别保留相同动态类型，但不共享可变字段。调用 public virtual 行为按动态类型分派。

## Cast

`is` 只检查声明关系和 reified 泛型信息。`as` 是显式可能失败的操作；安全 cast 的公开形式由最终语法提案确定，在定稿前规范示例不假设 `as?`。

## Bottom 与 Never

Throw 和不返回函数在控制流上不正常完成。实现可以内部使用 bottom/Never 类型进行合并，但是否暴露为可声明 public 类型仍未定稿。
