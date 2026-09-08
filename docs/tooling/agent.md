---
title: Agent 开发入口
description: AI Agent 使用 Norm 的规范、语义工具与验证入口
---

# Agent 开发入口

[Norm-Skill](https://github.com/normlanguage/Norm-Skill) 提供 CLI、语言规则和库清单的任务索引。源码通过 `cli/norm-skill` 子模块维护，获取方式为 `git submodule update --init cli/norm-skill`。Skill 内容只在独立仓库维护。

CLI 用法以安装版 `norm -h` 为入口；各命令使用 `norm <command> -h`，重构类型使用 `norm refactor name -h`。帮助不会执行项目操作。

使用当前工具链支持的语言与 API。能力成熟度见 [Status](/status)，工具设计原则与规划见 [Agent 工具设计](/design/agent-tooling)。源码工作区中的能力不代表已进入正式发行包。

## 学习与查询

[语义查询](/tooling/semantic-query)提供项目概览、声明搜索、强引用选择与上下文。

[语义重构预检](/tooling/rename-preview)提供基于文档修订的编辑预览与静态验证。

- [Language Tour](/learn/)：连续学习与可执行例子；
- [语言参考](/spec/language-spec)：精确语法与语义；
- [设计原则](/guide/design-principles)：类型、信息和分层边界；
- [标准库](/stdlib/overview)：当前模块入口；
- [API 文档导出](/tooling/api-documentation)：从语义模型获取公开声明与文档；
- [VS Code](/guide/vscode)：补全、签名、导航、引用与重命名。

类型关系优先查阅 [value、class 与 ref 的语义](/spec/value-identity-semantics)、[空值与推断](/learn/nullability-inference)、[函数](/learn/functions)和[错误处理](/learn/errors)。不要从其他语言推定 Norm 行为。

## 验证

静态检查、定向测试与机器输出见[检查与测试](/tooling/verification)。测试声明和关联见[测试 API](/stdlib/testing-api)。项目内可执行文档例子的组织和验证命令见 [Norm 测试索引](https://github.com/normlanguage/Norm/blob/main/norm/tests/README.md)。

验证结论必须说明实际执行范围。编译成功只证明静态约束，测试通过只证明被执行用例的断言；修改后需重新验证对应源码。

可复用的任务准备、独立验收与测量边界见 [Agent 任务基准](/tooling/agent-benchmark)。
