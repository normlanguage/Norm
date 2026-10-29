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

## 从这里开始

- **学习语言：** 按照[学习路径](https://normlanguage.github.io/Norm/zh/learn/)上手。
- **获取工具：** 在[发行页面](https://github.com/normlanguage/Norm/releases)获取 CLI 和配套 VS Code 扩展。源码构建见[发行与构建指南](docs/zh/design/distribution-source-build.md)。
- **开发应用：** 浏览[应用示例](https://github.com/normlanguage/examples)，通过[包注册表](https://github.com/normlanguage/registry)发现模块。

**持续开发中。** 已交付能力与实现边界以[当前状态](https://normlanguage.github.io/Norm/zh/status)和[版本索引](https://normlanguage.github.io/Norm/zh/versions/)为准。

## Norm 生态

以下仓库是语言生态的唯一入口：

| 入口 | 用途 |
| --- | --- |
| [Awesome Norm](https://github.com/normlanguage/awesome-norm) | 基于 Norm 构建的应用与高层库 |
| [Norm Skill](https://github.com/normlanguage/Norm-Skill) | 面向编程代理的参考驱动指引 |
| [Examples](https://github.com/normlanguage/examples) | 集成验收与可运行应用用例 |
| [Package Standards](https://github.com/normlanguage/package-standards) | 包的维护与交付要求 |
| [Registry](https://github.com/normlanguage/registry) | 模块所有权、不可变发布与共享包自动化 |

## 参与 Norm

本仓库包含语言规范、编译器与 CLI、标准库、VS Code 扩展、文档和测试。[examples](https://github.com/normlanguage/examples)维护应用示例与生态验收；适配包在 [Norm 组织](https://github.com/normlanguage)中独立维护。

| 内容 | 入口 |
| --- | --- |
| 编译器、运行时与 CLI | [cli/compiler](cli/compiler/) |
| 编辑器扩展 | [cli/extensions/vscode](cli/extensions/vscode/) |
| 标准库 | [norm/stdlib](norm/stdlib/) |
| 可执行语言测试 | [norm/tests](norm/tests/) |
| 文档与已检查示例 | [docs](docs/) · [norm/tests/docs](norm/tests/docs/) |

请阅读[编译器架构](https://normlanguage.github.io/Norm/zh/spec/compiler-design)、[实现策略](https://normlanguage.github.io/Norm/zh/design/implementation-strategy)和[贡献指南](https://normlanguage.github.io/Norm/zh/community)，并在 [Issues](https://github.com/normlanguage/Norm/issues)中提供最小复现。

## 许可证

[MPL-2.0](LICENSE)。许可范围和源码可用性见 [LICENSING.md](LICENSING.md)。
