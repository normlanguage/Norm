---
title: 语义查询
description: 使用限定名称查询声明并按需展开语义事实
---

# 语义查询

```bash
norm query -h
norm query path/to/module
norm query path/to/module --search amount
norm query path/to/module app.orders.amount
norm query path/to/module app.orders.amount --source --references
norm query path/to/module "app.orders.amount(Integer).value"
```

查询始终输出 JSON。无选择器时返回项目概览和声明清单；--search 搜索声明名称；位置参数按限定名称精确选择。检查与查询共用项目加载，不执行业务入口。即使源码包含错误，也保留能够确定的查询结果和诊断。

## 限定名称

限定名称从源码包名与语义所属关系派生，例如 app.Order、app.Order.code、app.orders.amount.value。无包声明时从顶层名称开始。参数、类型参数和局部变量可精确选择，默认搜索仍排除这些内部声明及 self。

搜索结果返回 qualifiedName、selector 和 at。重载通过带参数类型的 selector 选择，例如 app.orders.amount(Integer)；内部声明沿用所属函数选择器，例如 app.orders.amount(Integer).value。带括号或空格的参数应在 shell 中加引号，优先复制工具返回的 selector。

多个声明匹配时返回 input_error 和 query.candidates，不自动选择第一项。同名局部变量、同显示类型的重载等仍有歧义时，增加 `--at "<uri>#<offset>"`，直接复制候选的 at 字段。位置为声明起点，从零开始，单位为 UTF-16。

限定名称每次针对当前源码解析。输出的 id 和 revision 用于标识本次事实，不要求调用方构造身份或摘要输入。名称与位置不是跨编辑持久身份。

## 按需展开

精确查询默认只返回声明种类、签名、类型契约、来源与文档。以下选项可组合：

| 选项 | 内容 |
| --- | --- |
| --source | 包含所选声明的最小声明源码区间 |
| --references | 语义引用位置 |
| --dependencies | 声明依赖 |
| --tests | 显式关联的测试 |

参数或局部变量的源码区间可能是其所属函数。没有请求的关联信息不计算、不返回。没有独立 context 命令。

--offset 默认 0，--limit 默认 20、范围 1 至 1000。搜索、歧义候选和各组展开结果分别返回 total、offset、hasMore；展开组共用分页参数、总数独立。跨页调用不承诺同一快照，源码变化后应重新查询。

关联测试不等于完整受影响测试，声明依赖不覆盖全部动态行为。外部声明可精确查询签名；contextAvailable 表明是否支持源码上下文展开。源码位置使用零基 UTF-16 半开区间，文档修订仅覆盖捕获文本的 UTF-8 SHA-256，不代表包含配置、资源与 Java 制品的完整项目快照。

## 实现与验证入口

- [SemanticQuery](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/SemanticQuery.java)：查询、选择和展开；
- [DeclarationNames](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/DeclarationNames.java)：名称和重载选择器；
- [AuthoringCommand](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/AuthoringCommand.java)：分层帮助与 CLI；
- [QualifiedAuthoringTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/QualifiedAuthoringTest.java)：真实源码命令验收。

公共诊断和失败结构见[检查与测试](/tooling/verification)。
