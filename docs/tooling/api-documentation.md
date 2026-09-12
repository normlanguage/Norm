---
title: API 文档导出
description: 从 Norm 语义模型生成可浏览的结构化模块文档
---

# API 文档导出

`norm docs` 从编译器语义模型读取公开声明及其 `@Document`，因此声明 identity、类型、参数、源码顺序和文档来自同一次编译，不需要维护另一份 API 描述。

## Markdown 引用检查

```bash
norm docs check path/to/markdown --module path/to/module
norm docs check path/to/markdown --format json
```

引用地址使用 `@模块名.导出文件.声明#版本`；GitHub 包增加 `github.` 前缀，例如 `@github.h2.database.xxfile.xxfunction#1`。外部引用按注册表中最长匹配的模块名前缀确定模块，再通过[包管理器](/ecosystem/package-manager)下载精确版本；不同版本分别分析。示例地址用于说明语法，不保证对应包或声明存在。

本地引用由 `--module` 指定源码模块，版本必须与模块声明一致。例如 @std.annotation.protocols.FieldTarget#1。文件部分使用模块的导出路径，不是 package 名；声明及成员仍由编译器语义模型解析。重载使用带类型签名的完整形式，例如 `@{std.math.integer.clamp(Integer,Integer,Integer)#1}`。

检查递归读取目录下的 `.md` 文件，跳过隐藏目录、隐藏文件和 `node_modules`。正文和链接文字中的引用参与检查；代码块、行内代码、HTML 标签与注释、链接地址、邮箱和转义的 `\@` 不参与。版本缺失、声明不存在、未公开导出、重载歧义或包解析失败会产生带 Markdown 位置的诊断与失败退出码。被引用模块须能通过当前工具链的语义分析。检查只验证声明引用，不验证自然语言描述与实现行为一致。

本站构建和开发预览通过 [VitePress 插件](https://github.com/normlanguage/Norm/blob/main/docs/.vitepress/markdown-references.ts)调用同一编译器入口。`@Document` 的定义仍见 [Annotation 规范](/spec/annotations)。

## 生成

指定目录必须直接包含 `module.norm`：

```bash
norm docs path/to/module --output path/to/api --strict
```

`--strict` 要求导出的公开声明及普通 callable 参数具有 `@Document`。编译错误或文档缺失时不会生成一部分结果。

输出目录完整映射源模块：根清单为 `module.api.json`，其余 `.norm` 文件在相同相对目录下生成同名 `.api.json`。例如 `collections/sequences.norm` 对应 `collections/sequences.api.json`。再次生成会以一棵完整的新树替换旧的生成结果。

JSON 的唯一结构契约是 [公共 Schema](/schemas/norm-api-v1.json)，模块清单和文件文档分别使用 [Module API Schema](/schemas/module-api-v1.json) 与 [File API Schema](/schemas/file-api-v1.json)。

## 浏览

VitePress 主题全局注册了 `NormModuleDocument`。组件只需要生成目录的公开 URL：

```vue
<NormModuleDocument root="/api/std/" />
```

组件读取 `module.api.json` 构建目录树，并在选择文件时加载对应的 `.api.json`。每个模块拥有独立的输出根目录和组件实例。

生成器分析模块的生产和测试源码集合，从 `@Test` 派生 `Unit tests` 关联，不执行测试。关联可跳转到对应测试文件中的源码；测试源码不作为公开 API 导出，也不要求 `@Document`。测试声明见 [测试 API](/stdlib/testing-api)。
