---
title: Agent Library
description: Responsibility boundaries and session storage extension points for the Norm Agent and OpenAI libraries
---

# Agent Library

`norm/libraries/openai` provides only the OpenAI protocol model, request, response, and tool descriptions. `norm/libraries/agent` builds the Agent loop, conversation sessions, tool scope, and remote Skill loading on top of it.

Sessions are defined by the `agent.SessionStore` persistence boundary. `agent.JsonSessionStore` is the default implementation and stores the message tree as JSON text. Database, file, and remote stores implement the same interface and are injected through `AgentSession(store: ...)`. The Agent loop only requires JSON content; the storage medium stays outside it.

MCP connects through `agent.McpToolProvider` into the same tool catalog used by the Agent. `agent.McpClient` discovers `tools/list` over JSON-RPC HTTP and routes `tools/call` back to the MCP service. The Agent rediscovers tools for each turn, so server-side additions and removals take effect on the next request.

The Agent does not own runtime updates, hot loading, state migration, or source publication. Those capabilities belong to the `Runtime` object obtained by the application from the system. An application may wrap runtime operations as ordinary tools or tool scopes before passing them to the Agent.

The library source is the single source of truth for public declarations:

- [Agent module](https://github.com/normlanguage/Norm/tree/main/norm/libraries/agent)
- [OpenAI protocol module](https://github.com/normlanguage/Norm/tree/main/norm/libraries/openai)

HTTP header configuration is owned by [Client](../../../norm/libraries/openai/client.norm) and exposed by [Agent](../../../norm/libraries/agent/agent.norm), using the standard [HeaderMap](../../../norm/stdlib/std/http/client.norm) type.
