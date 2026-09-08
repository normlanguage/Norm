---
title: Agent 任务基准
description: 独立提交、行为验收与可追溯执行证据
---

# Agent 任务基准

基准以可编辑项目和独立验收区分任务执行与评分。任务覆盖功能新增、类型错误修复、语义重命名、API 变更和测试补充。任务说明、初始源码和验收的唯一入口是 [agent-tasks.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/fixtures/agent-tasks.mjs)。

## 准备与验收

```bash
node cli/compiler/scripts/agent-benchmark.mjs prepare add_feature .tmp/agent-run/add_feature
node cli/compiler/scripts/agent-benchmark.mjs verify add_feature .tmp/agent-run/add_feature launcher-argv.json .tmp/agent-run/add_feature-report.json
```

准备目标目录必须不存在。准备阶段只生成 TASK.md 和 app 项目，验收源码不会进入提交目录。Agent 按 TASK.md 修改项目，可使用[语义查询](/tooling/semantic-query)、[语义重构预检](/tooling/rename-preview)和[检查与测试](/tooling/verification)。

launcher 文件包含启动 CLI 的完整 argv 数组，例如 `["C:/tools/norm.exe"]`。开发分发包使用 Java 25 可执行文件、JVM 参数、module path 和主模块参数，具体值参考生成的启动脚本；基准不通过 shell 拼接命令，也不直接执行 `.bat`。启动器路径使用绝对路径，版本必须支持当前机器输出契约。

验收在报告旁创建独立证据目录，复制提交后注入验收，保留捕获文件摘要、启动参数、每次 CLI 调用、进程退出码、原始输出、耗时和字节数。报告文件必须不存在，不覆盖旧证据。

测试补充任务要求生产源码保持不变、测试能够独立执行且关联目标声明，并用提交测试分别拒绝负数、零和正数行为的错误实现。API 变更任务检查旧调用不再编译；重命名任务检查旧声明没有被兼容包装保留。

结果分为 passed、failed 和 invalid。启动失败、输出协议损坏或编译器基础设施失败属于 invalid，不能计入有效任务失败率。脚本返回码分别为 0、1、2。运行器实现及测试见 [agent-benchmark.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/agent-benchmark.mjs) 和 [agent-benchmark.test.mjs](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/agent-benchmark.test.mjs)。

## 测量边界

行为验收通过证明该提交满足这些用例，不等同于 Agent 成功率或性能提升。报告的 durationMs 是 CLI 调用耗时，输出字节数不是模型 token 数。

比较 Agent 表现时，固定任务、模型、提示和工具链输入，为每次尝试创建独立提交目录，并从实际 Agent 会话记录模型版本、修复轮数、完整耗时及 token 用量。无法取得的指标保持缺失，不从字节数推算；排除 invalid 运行，并保留失败尝试的证据。

维护者同时编写验收并完成任务的走查用于确认工具与基准可运行，应与独立或盲测的模型评估分开报告。没有独立测量时不宣称相较其他工具或模型有所提升。
