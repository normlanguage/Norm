---
title: API Documentation Export
description: Generate browsable structured module docs from the Norm semantic model
---

# API Documentation Export

`norm docs` reads public declarations and their `@Document` annotations from the compiler semantic model. Declaration identity, types, parameters, source order, and documentation therefore come from one compilation without maintaining another API description.

## Markdown reference checking

```bash
norm docs check path/to/markdown --module path/to/module
norm docs check path/to/markdown --format json
```

A reference address has the form `@module-name.exported-file.declaration#version`. A GitHub package adds a `github.` prefix, for example `@github.h2.database.xxfile.xxfunction#1`. The longest matching module-name prefix in the registry identifies an external module, whose exact version is downloaded through the [package manager](/ecosystem/package-manager). Different versions are analyzed separately. These sample addresses illustrate syntax; they do not guarantee that a corresponding package or declaration exists.

`--module` selects the source module for local references, and the reference version must match the module declaration. For example: @std.annotation.protocols.FieldTarget#1. The file portion uses the module's export path, not its package name; declarations and members are still resolved by the compiler semantic model. An overloaded function needs its complete typed signature, such as `@{std.math.integer.clamp(Integer,Integer,Integer)#1}`.

Checking recursively reads `.md` files in a directory, skipping hidden directories, hidden files, and `node_modules`. References in body text and link labels are checked; code blocks, inline code, HTML tags and comments, link destinations, email addresses, and escaped `\@` are excluded. A missing version, nonexistent declaration, unpublished export, ambiguous overload, or package-resolution failure yields a diagnostic at the Markdown location and a failing exit code. The referenced module must be analyzable by the current toolchain. This checks declaration references, not whether prose accurately describes implementation behavior.

This site's build and development preview call the same compiler entry point through the [VitePress plugin](https://github.com/normlanguage/Norm/blob/main/docs/.vitepress/markdown-references.ts). The [annotation specification](/spec/annotations) defines `@Document`.

## Generation

The specified directory must directly contain `module.norm`:

```bash
norm docs path/to/module --output path/to/api --strict
```

`--strict` requires `@Document` on exported public declarations and ordinary callable parameters. Compile errors or missing documentation prevent a partial result from being generated.

The output directory mirrors the complete source module. `module.api.json` is the root manifest; every other `.norm` file produces a same-named `.api.json` at the same relative path. For example, `collections/sequences.norm` maps to `collections/sequences.api.json`. A subsequent generation replaces the previous output with one complete new tree.

The single structural contract for JSON is the [public schema](/schemas/norm-api-v1.json). The module manifest and file documents use the [Module API schema](/schemas/module-api-v1.json) and [File API schema](/schemas/file-api-v1.json), respectively.

## Browsing

The VitePress theme registers `NormModuleDocument` globally. A component needs only the public URL of the generated directory:

```vue
<NormModuleDocument root="/api/std/" />
```

The component reads `module.api.json` to build a directory tree and loads the matching `.api.json` when a file is selected. Each module has its own output root and component instance.

English `@Document` text remains the source description. The Chinese browser uses the [Chinese description catalog](https://github.com/normlanguage/Norm/blob/main/docs/translations/zh-CN/api.json); the [API build check](https://github.com/normlanguage/Norm/blob/main/docs/scripts/check-norm-api.mjs) requires a translation for every generated description and rejects obsolete entries.

The generator analyzes both production and test source sets. It derives `Unit tests` associations from `@Test` without running tests. Associations link to the corresponding test-source file; test sources are not exported as public API and need no `@Document`. See the [Testing API](/stdlib/testing-api) for test declarations.
