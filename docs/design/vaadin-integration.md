---
title: Vaadin UI backend
description: Shared UI protocols and the Vaadin backend
---

# Vaadin UI backend

[`ui.web`](https://github.com/normlanguage/ui.web) implements the backend-neutral [`ui`](https://github.com/normlanguage/ui) protocols with Vaadin Flow. Widget composition, field observation, bindings, keyed reconciliation and resource ownership belong to `ui`; the web backend owns browser nodes, session scheduling and host integration.

The backend consumes [`ui.theme`](https://github.com/normlanguage/ui.theme) through the shared UI theme context. It does not depend on `ui.fx` or `ui.fx.kit`.

Implementation and acceptance entry points:

- [Module and backend sources](https://github.com/normlanguage/ui.web/tree/main/ui/web).
- [Host integration and lifecycle](https://github.com/normlanguage/ui.web/tree/main/src/main/java).
- [Runnable applications](https://github.com/normlanguage/ui.web/tree/main/samples).
- [Host tests](https://github.com/normlanguage/ui.web/tree/main/src/test).
- [Build instructions and supported execution modes](https://github.com/normlanguage/ui.web).

Java integration uses the existing [adapter mechanism](java-library-adapters.md). Vaadin-specific types and scheduling remain in the backend repository; the compiler has no Vaadin-specific behavior.
