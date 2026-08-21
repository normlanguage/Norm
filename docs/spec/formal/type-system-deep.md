# 类型系统深入规则

本页说明类型组合处容易产生歧义的边界：nullable、泛型、函数和动态类型。

## Nullable 组合

nullable 标记作用于完整类型：`List<String>?` 与 `List<String?>` 不同。重复 nullable `T??` 不形成新类型，应规范化为 `T?` 或直接诊断冗余。

`ref<T>` 的 nullable 组合必须由引用与 nullable 规范共同确定，不能由实现自行推断。

## 捕获转换

读取 `List<? extends Shape>` 时，编译器为通配符建立新捕获类型 α，满足 `α <: Shape`。读取结果可以作为 Shape，写入除不存在值外不安全。

对 `List<? super Circle>` 建立 `Circle <: β`。可以写入 Circle，但读取只得到未知 β，必须通过适当接口或显式模式处理。

## Reified 泛型

`List<String>` 与 `List<int>` 的运行时描述不同。运行时类型检查、反射和序列化可读取实际参数；实现不能以擦除后附加不可靠外部 token 代替。

## 函数类型

```norm
String formatter(Point value)
```

函数类型由返回类型和参数类型序列决定，参数名用于命名调用兼容性。匿名函数没有任意 lexical capture；绑定方法引用显式携带接收者。

## 动态分派与复制

父类型或 interface 变量保存完整动态类型。复制 class 值后，两个副本分别保留相同动态类型，但不共享可变字段。调用 public virtual 行为按动态类型分派。

## Cast

`is` 只检查声明关系和 reified 泛型信息。`as` 是显式可能失败的操作；安全 cast 是否使用 Result/Option 形式由最终语法提案确定，在定稿前规范示例不假设 `as?`。

## Bottom 与 Never

Throw 和不返回函数在控制流上不正常完成。实现可以内部使用 bottom/Never 类型进行合并，但是否暴露为可声明 public 类型仍未定稿。
