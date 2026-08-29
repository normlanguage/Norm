---
title: API 文档导出
description: 从 Norm 语义模型生成可浏览的结构化模块文档
---

# API 文档导出

`norm docs` 从编译器语义模型读取公开声明及其 `@Document`，因此声明 identity、类型、参数、源码顺序和文档来自同一次编译，不需要维护另一份 API 描述。

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
