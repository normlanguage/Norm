# 语句语法

语句执行动作但不作为外层表达式的值。核心语句包括变量声明、赋值、表达式语句、return、break、continue、throw 和控制结构语句。

## 变量与赋值

```norm
int count = 0
count = count + 1
```

非空局部变量必须在声明时初始化。赋值目标必须是可写局部变量、class 字段、Array/List 元素或由 API 明确定义的可写位置。value 字段不可赋值。

## 表达式语句

函数或方法调用可以作为语句使用：

```norm
logger.info(event: "started")
```

丢弃具有重要 Result 返回值的调用应产生警告，除非调用点显式使用标准库 discard 函数说明意图。

## Return

`return` 结束当前函数。void 函数使用 `return` 或正常到达末尾，其他函数使用 `return expression`。return 不会从匿名嵌套函数返回到外层函数。

## Break 与 Continue

无值 break 结束最近循环语句，continue 进入下一次迭代。`break expression` 只在正在求值的 if、for 或 switch 表达式中合法。

## Throw

`throw expression` 结束当前路径并开始异常查找。throw 路径不要求满足当前函数的普通返回值规则。

不可达语句产生诊断；变量作用域从声明后开始，到所在代码块结束。
