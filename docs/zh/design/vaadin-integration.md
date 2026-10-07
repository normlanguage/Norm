---
title: Vaadin UI 后端
description: 通用 UI 协议与 Vaadin 后端的职责边界
---

# Vaadin UI 后端

[`ui.web`](https://github.com/normlanguage/ui.web) 使用 Vaadin Flow 实现与后端无关的 [`ui`](https://github.com/normlanguage/ui) 协议。Widget 组合、字段观察、绑定、键控协调和资源所有权由 `ui` 统一管理；Web 后端负责浏览器节点、会话调度和宿主接入。

后端通过通用 UI 主题上下文消费 [`ui.theme`](https://github.com/normlanguage/ui.theme)，不依赖 `ui.desktop` 或 `ui.desktop.kit`。

实现与验收入口：

- [模块与后端源码](https://github.com/normlanguage/ui.web/tree/main/ui/web)。
- [宿主接入与生命周期](https://github.com/normlanguage/ui.web/tree/main/src/main/java)。
- [可运行应用](https://github.com/normlanguage/ui.web/tree/main/samples)。
- [宿主测试](https://github.com/normlanguage/ui.web/tree/main/src/test)。
- [构建方式与支持的运行形态](https://github.com/normlanguage/ui.web)。

Java 接入复用现有[适配机制](java-library-adapters.md)。Vaadin 专用类型和调度留在后端仓库，编译器不包含 Vaadin 专用行为。
