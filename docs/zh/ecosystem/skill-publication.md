---
title: 仓库 Skill 发布
description: 让 Agent 指南和拥有 API 的库一起发布
---

# 仓库 Skill 发布

每个公开 Norm 库都要在自己的仓库中发布 Skill。仓库同时包含
`module.norm`、源码和根目录 `SKILL.md`；一个仓库包含多个公开模块时，
也可以在各模块目录旁提供生成的模块 Skill。

Skill 使用仓库固定的 Norm 工具链生成：

```text
norm docs path/to/module --output build/api --skill SKILL.md --strict
```

声明和 `@Document` 元数据是 API 的唯一事实来源。提交到仓库的
`SKILL.md` 是可复现的发布产物，CI 必须在干净检出中重新生成并在结果不
一致时失败。Skill 至少说明模块身份和版本、可用 import、公开签名、参数
含义、生命周期和所有权、失败行为以及经过验证的示例；需要更多细节时，
链接到本仓库的源码和测试。

Registry 条目只维护 `name`、`owner`、`repository`。Agent 通过 Registry
找到库仓库，再读取该仓库的不可变版本 Skill：

```text
https://raw.githubusercontent.com/{owner}/{repository}/v{version}/SKILL.md
```

`main` 地址只用于开发检查。可复现构建应使用版本标签或提交地址。聚合器
可以索引各仓库的 Skill，但不复制 Skill，也不成为库 Skill 的所有者。
