# 核心求值规则

Norm 使用确定性的从左到右求值。编译器优化不得改变异常、函数调用、对象修改或外部 I/O 的可观察顺序。

## 调用

对于 `f(a: e1, b: e2)`：

1. 解析唯一目标函数；
2. 按源码出现顺序求值实参；
3. 按参数名建立新局部环境；
4. value 实参建立逻辑独立值，class 实参保留对象身份；
5. 执行函数体直到 Return、Throw 或 Void 正常完成。

## If

先求值 Boolean 条件，只执行一个分支。作为表达式时，被执行分支必须以 Value 或不正常完成结果结束，编译器不为缺失 else 插入 null。

## For

迭代表达式只求值一次并取得迭代器。每次迭代创建新的循环变量绑定。continue 请求下一元素，无值 break 正常结束语句循环，break value 结束表达式循环。耗尽时执行可选 else。

## Switch

被匹配表达式只求值一次。case 按源码顺序检查，首个匹配 case 独占执行且不 fallthrough。每个 switch 在静态阶段已保证穷尽；表达式 case 的正常完成路径必须以 `break value` 产生结果。

## 异常与 Finally

Throw 沿调用栈寻找首个兼容 catch。离开 try 时执行 finally；finally 正常完成后恢复原完成结果，finally 自己 Return 或 Throw 时替代原结果。

## 赋值

先确定目标位置，再求值右侧。value 写入逻辑独立值，class 写入对象引用。失败的右侧求值不修改目标。完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。
