# 编译器架构

Norm 编译器把语法、静态规则和运行时后端分离。所有后端共享同一类型检查和 Typed IR，避免解释器与原生编译器发展出不同语言。

```text
源码
 ↓
Lexer → Token
 ↓
Parser → AST
 ↓
名称解析与声明图
 ↓
类型检查与流分析
 ↓
Typed IR
 ↓
解释器 / 原生后端
```

## 前端

Lexer 负责 UTF-8、注释、标识符和字面量。Parser 只建立语法结构并恢复可报告错误，不在解析阶段猜测类型。

名称解析建立 module、import、作用域、overload set 和名义类型图。解析结果为每个名称引用绑定唯一声明或给出歧义诊断。

## 语义分析

- 类型检查和安全数值转换；
- 泛型约束求解与 use-site variance；
- overload resolution 与命名参数；
- nullable 流分析和确定赋值；
- enum switch 穷尽检查；
- control expression 路径和值类型合并；
- value/class/Ref 合法性验证。

## Typed IR

IR 中每个值携带静态类型，每个调用已解析目标，每个泛型已替换或保存 reified 参数。普通值复制和 Ref 共享使用不同节点，后端不能重新猜测。

控制表达式降低为显式基本块和 value edge；Return、Throw、BreakValue 分开表示。finally 在 IR 中保留所有完成路径的清理边。

## 后端

参考解释器用于快速验证语义和测试规范。原生后端负责布局、调用约定、GC/内存管理与平台 ABI。两者运行同一 conformance suite。

## 优化

允许复制消除、逃逸分析、写时复制、内联、常量折叠和死代码消除。优化前后必须保持：class 修改隔离、Ref identity、从左到右可观察顺序、异常边界和 reified 泛型信息。

## 诊断与工具

编译器输出稳定错误 code、主要位置、相关声明位置和修复提示。Parser、formatter、LSP 和文档代码测试应共享 grammar/AST 定义，避免工具各自实现语言子集。

## 增量构建

模块接口摘要包含 public 签名、类型关系、enum variant 和必要 metadata。只有摘要变化才使下游重新类型检查；private 实现变化只重新编译当前模块。
