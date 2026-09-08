---
title: 语义查询
description: 项目声明、文档修订与最小语义上下文
---

# 语义查询

```bash
norm query path/to/module
norm query path/to/source.norm --search answer --limit 20
norm query path/to/module --symbol "<id>" --document "<uri>" --revision "<revision>" --source
```

`query` 始终输出 JSON。项目加载与检查共用入口，不执行业务代码；不提供选择器时输出项目概览，包含项目根、分析入口、模块依赖、文档归属及文档修订。搜索或查询精确上下文时只返回对应结果，不重复整份项目清单。目录入口分析完整模块。编译诊断与能够确定的查询结果同时返回，非零退出码不能简单解释为没有查询结果。

## 搜索与选择

`--search` 对声明名称执行不区分大小写的子串搜索，省略时列出项目声明。默认排除局部变量、参数、类型参数和 self；预加载标准库不作为用户项目声明列出。相同名称的重载保留独立身份，不自动选择第一个候选。

使用结果中的 `id`、`location.uri` 和 `revision` 进行上下文查询。`--symbol`、`--document`、`--revision` 必须一起提供，不能与 `--search` 混用。修订是捕获的文档文本以 UTF-8 编码后的 SHA-256，不能用作包含配置、资源和外部制品的完整项目快照令牌。每次调用重新加载和分析项目，目标文档变化返回 `conflict`。

## 上下文

声明结果提供类型身份、参数类型、默认参数标志及泛型参数约束。机器调用方应读取这些结构化字段，展示用 signature 不包含所有默认值细节。

上下文默认包含精确声明、已解析的声明依赖、引用位置与 `@Test` 关联。`--source` 额外返回包含所选声明的最小声明源码区间，不默认展开整个文件。源码区间和引用位置使用从零开始的 UTF-16 偏移，结束位置不包含在区间内。

外部声明可提供签名但不一定具有当前项目的源码修订；读取 `contextAvailable` 决定是否能够继续查询。依赖来自作者态解析关系，不代表完整 Core 依赖图或动态运行覆盖；测试关联不等于所有受影响测试。

`--offset` 与 `--limit` 控制结果页，默认分别为 0 与 20，limit 范围为 1 至 1000。每组结果分别返回 total、offset 和 hasMore。上下文的依赖、引用、关联测试使用同一分页参数，各组总数独立计算。多页查询之间发生源码变化时，应重新查询，不把不同修订的页面当作同一快照。

## 实现与验证入口

- [SemanticQuery](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/SemanticQuery.java)：查询和上下文；
- [SemanticQueryWriter](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/component/SemanticQueryWriter.java)：结果字段；
- [AuthoringOptions](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/AuthoringOptions.java)：参数契约；
- [SemanticQueryTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/SemanticQueryTest.java)与 [QueryCommandTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/QueryCommandTest.java)：语义及真实源码 CLI 验收。

诊断和失败结果复用[检查与测试](/tooling/verification)的公共结构。
