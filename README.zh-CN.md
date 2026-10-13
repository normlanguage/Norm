<p align="center">
  <img src="docs/public/brand/norm-banner.svg" alt="Norm — 熟悉的语法，明确的语义。" width="100%">
</p>

<p align="center">
  <a href="https://normlanguage.github.io/Norm/zh/learn/">学习 Norm</a> ·
  <a href="https://normlanguage.github.io/Norm/zh/spec/language-spec">语言参考</a> ·
  <a href="https://github.com/normlanguage/examples">应用示例</a> ·
  <a href="https://github.com/normlanguage/Norm/releases">下载发行版</a> ·
  <a href="README.md">English</a>
</p>

# Norm

Norm 是一门静态类型、面向应用的编程语言，采用熟悉的语法，表达明确的语义。

用 `class` 表达身份，`value` 表达数据，`enum` 表达可选状态，`interface` 表达能力，`ref` 表达受控别名。通过[语言教程](https://normlanguage.github.io/Norm/zh/learn/)中的可执行示例逐步探索。

```norm
main() {
  printLine("Hello, Norm")
}
```

[运行这个示例](norm/tests/docs/showcase/simple.norm) · [探索应用示例](https://github.com/normlanguage/examples)

## 开始使用

- **学习语言：** 按照[学习路径](https://normlanguage.github.io/Norm/zh/learn/)上手。
- **获取工具：** 在[发行页面](https://github.com/normlanguage/Norm/releases)获取 CLI 和配套 VS Code 扩展。源码构建见[发行与构建指南](docs/zh/design/distribution-source-build.md)。
- **开发应用：** 浏览[应用示例](https://github.com/normlanguage/examples)和[包注册表](https://github.com/normlanguage/registry)。

**持续开发中。** 已交付能力与实现边界以[当前状态](https://normlanguage.github.io/Norm/zh/status)和[版本索引](https://normlanguage.github.io/Norm/zh/versions/)为准。

## 深入探索

| 入口 | 内容 |
| --- | --- |
| [语言参考](https://normlanguage.github.io/Norm/zh/spec/language-spec) | 语言规则与语义 |
| [标准库](https://normlanguage.github.io/Norm/zh/stdlib/overview) | 核心类型与标准库 API |
| [Agent 工具](https://normlanguage.github.io/Norm/zh/tooling/agent) | 查询、重构与验证 Norm 代码 |
| [Awesome Norm](https://github.com/normlanguage/awesome-norm) | 项目与生态资源 |
| [Norm Skill](https://github.com/normlanguage/Norm-Skill) | 面向编程 Agent 的参考驱动指南 |

## 参与开发

本仓库维护语言规范、编译器与 CLI、标准库、VS Code 扩展、文档和测试。应用示例与生态验收位于 [examples](https://github.com/normlanguage/examples)；适配包在 [Norm 组织](https://github.com/normlanguage)下独立维护。

| 源码 | 入口 |
| --- | --- |
| 编译器、运行时与 CLI | [cli/compiler](cli/compiler/) |
| 编辑器扩展 | [cli/extensions/vscode](cli/extensions/vscode/) |
| 标准库 | [norm/stdlib](norm/stdlib/) |
| 可执行语言测试 | [norm/tests](norm/tests/) |
| 文档与经过验证的示例 | [docs](docs/) · [norm/tests/docs](norm/tests/docs/) |

阅读[编译器架构](https://normlanguage.github.io/Norm/zh/spec/compiler-design)、[实现策略](https://normlanguage.github.io/Norm/zh/design/implementation-strategy)和[贡献指南](https://normlanguage.github.io/Norm/zh/community)。报告问题时，请在 [Issues](https://github.com/normlanguage/Norm/issues) 中提供最小示例。

## 许可协议

[MPL-2.0](LICENSE)。参见[许可范围与源码提供方式](LICENSING.md)。
