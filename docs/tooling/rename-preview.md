---
title: 语义重构预检
description: 限定名称选择与共享重构预览
---

# 语义重构预检

每种重构使用子命令和相同预览契约，完整参数直接查看帮助：

```bash
norm refactor -h
norm refactor name -h
norm refactor name path/to/module app.orders.amount --to total --preview
```

当前类型 name 执行语义重命名。限定名称和歧义选择规则见[语义查询](/tooling/semantic-query)。--to 必填；默认行为与显式 --preview 相同，只生成编辑和静态验证，不修改磁盘。--apply 尚未提供。

名称无效、选择存在歧义、同作用域命名冲突或涉及只读生成源码时失败。名称解析绑定本次捕获的声明身份与文档修订，调用方无需提供身份和摘要。命名实参通过已解析调用关联到参数声明，参数重命名同时修改这些调用标签。

## 预览与验证

结果中的 `refactor.inputs` 标识本次捕获的源码文本修订，`changes` 为每个修改文件提供 URI、前后修订及全部替换。替换使用从零开始的 UTF-16 偏移，结束位置不包含在区间内，并按原文位置从后向前排序；oldText 和 newText 分别表示被替换文本和新文本。

验证以同一份 `CompilationRequest` 的源码覆盖层进行静态分析，不重新扫描文件或执行程序。`beforeDiagnostics` 对应修改前，顶层 `diagnostics` 对应预览后的源码。原有错误不会阻止生成预览；预览后仍有编译错误时返回编译失败状态，同时保留编辑计划和两份诊断，不能据此声称编辑可直接交付。

文档修订只覆盖源码文本。模块配置求值、依赖解析和编译作用域来自本次项目加载，输入清单不是包含外部资源与 Java 制品的完整快照。预检通过只证明该捕获输入上的静态约束，不证明运行行为，也不证明随后变化的文件。

## 消费编辑

调用方应用预览前应校验捕获源码的修订，并确认每个替换区间与 oldText 一致；内容变化后重新获取预览。完成修改后重新检查并执行相关测试。当前 CLI 不提供磁盘应用事务，跨文件写入失败恢复由实际编辑执行方承担。

字段与语义的唯一实现入口是 [RefactorPreview](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/RefactorPreview.java)、[LanguageService](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/LanguageService.java) 和 [SemanticQueryWriter](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/component/SemanticQueryWriter.java)。输入捕获、跨文件编辑和已有错误的验证见 [RenamePreviewTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/RenamePreviewTest.java)。
