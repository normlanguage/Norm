---
title: 检查与测试
description: Norm 静态检查、定向测试与结构化反馈
---

# 检查与测试

```bash
norm check path/to/module --format json
norm check path/to/source.norm
norm test path/to/module --filter package.function --format json
```

`check` 分析项目生产和测试源码，不要求 `main`，不执行业务入口或测试。模块配置仍会求值，依赖仍按普通项目规则解析。目录入口必须直接包含 `module.norm`；文件入口必须是业务或测试源码。

`test` 编译并执行测试，`--filter` 限定 package 或函数。测试声明与筛选语义见[测试 API](/stdlib/testing-api)。

## 输出契约

两种命令默认使用文本输出，`--format json` 将 stdout 保留为一个 JSON 对象。测试通过 Norm 执行上下文输出的日志进入 stderr；Java 库直接写入进程标准输出不经过该路由。直接消费 CLI 输出，避免把 Gradle 等启动工具的日志当作命令 JSON。

机器结果包含协议版本、工具链版本、命令、状态、退出码与诊断；完成测试时包含测试统计及失败项，命令失败时包含失败原因。字段的唯一实现入口是 [CommandReportWriter](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/component/CommandReportWriter.java)，状态与退出码见 [CommandReport](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/value/CommandReport.java)。契约验收见 [StructuredCommandsTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/StructuredCommandsTest.java)。

诊断位置使用文档 URI 和从零开始的 UTF-16 字符偏移，结束位置不包含在区间内。运行异常的行、列沿用运行时从一开始的坐标。错误码、关联诊断和 notes 来自编译器，不从文本解析。

未发现测试返回失败退出码和 `no_tests` 状态。编译错误与测试失败可能具有相同退出码，机器调用方应读取 `status` 判断具体结果。

结果对应本次调用捕获并分析的输入，不代表之后编辑过的源码。当前结果没有完整输入快照标识，不可作为跨调用编辑的前置版本令牌。
