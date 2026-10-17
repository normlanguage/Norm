---
title: Agent 库
description: Norm Agent 库与 OpenAI 协议库的职责边界及会话存储扩展点
---

# Agent 库

`norm/libraries/openai` 只提供 OpenAI 协议模型、请求、响应和工具描述；`norm/libraries/agent` 在其上提供 Agent 循环、对话会话、工具作用域和远程 Skill 加载。

会话由 `agent.SessionStore` 持久化边界定义。`agent.JsonSessionStore` 是默认实现，以 JSON 文本保存消息树；数据库、文件或远程存储只需要实现同一接口，然后通过 `AgentSession(store: ...)` 注入。`AgentSession` 只要求存储内容是 JSON 数组，具体存储介质不进入 Agent 循环。

MCP 通过 `agent.McpToolProvider` 接入 Agent 的同一工具目录。`agent.McpClient` 使用 JSON-RPC HTTP 传输发现 `tools/list`，并把 `tools/call` 路由回 MCP 服务；Agent 每轮重新发现工具，所以服务端新增或移除工具会进入下一轮请求。

公开声明的唯一来源是库源码：

- [Agent 模块](https://github.com/normlanguage/Norm/tree/main/norm/libraries/agent)
- [OpenAI 协议模块](https://github.com/normlanguage/Norm/tree/main/norm/libraries/openai)

