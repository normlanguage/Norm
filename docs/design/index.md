---
title: Compiler Design
description: Norm frontend, Canonical Core, and Truffle backend
---

# Compiler Design

Norm separates authoring semantics from executable content identity. Source first forms a shared semantic model, then freezes into canonical, content-addressed Core; Truffle executes only resolved Core.

```text
Source
  ↓
Syntax
  ↓
Semantic Model
  ↓
Bound Representation
  ↓
Canonical Core IR
  ↓
Runtime: execution contracts → Truffle → platform adapter
```

## Reading guide

- [Compiler architecture](/spec/compiler-design): complete pipeline, identity boundaries, and incremental model.
- [Implementation strategy](/design/implementation-strategy): technology choices and dependency direction.
- [Toolchain development standard](/design/toolchain-development): module responsibilities and verification constraints.
- [Distribution source-build architecture](/design/distribution-source-build): the sole Gradle build entry, offline distribution builds, and release-equivalence acceptance.
- [Block call chains](/design/block-call-chains): the restricted omitted-dot closure-chain decision, implementation index, migration, and acceptance.
- [Agent tooling design](/design/agent-tooling): precision, strong references, context efficiency, and verification contracts.
- [Sample system](/design/sample-system): ownership and acceptance for website, generated, language, learning, and library examples.
- [System runtime architecture](/design/system-runtime): I/O, resources, and platform adapters.
- [Serialization runtime architecture](/design/serialization-runtime): structure metadata and mapping.
- [Java Library Adapter](/design/java-library-adapters): single-root JARs, ordinary Module identity, content addressing, and publication boundaries.
- [Vaadin integration plan](/design/vaadin-integration): field-responsive pages, standalone Jetty, Spring integration, and staged acceptance.
- [Compiler bootstrap plan](/design/bootstrap-plan): the self-hosting boundary.
- [Application startup performance](/design/startup-performance): source-run baseline, artifact reuse boundaries, and acceptance.

Performance goals contain only verifiable budgets, not unmeasured conclusions inferred from architecture. See [Status](/status) for current public capabilities.
