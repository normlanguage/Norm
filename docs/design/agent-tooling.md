---
title: Agent Tool Design
description: Principles for semantic queries, edits, and verification by AI agents
---

# Agent Tool Design

Agent tools should reduce guessing, irrelevant context, and ineffective attempts. Operations should use explicit semantic facts and yield independently verifiable results. The [Language Reference](/spec/language-spec) continues to define language rules; the [agent development entry](/tooling/agent) describes the current available surface.

## Principles

| Principle | Contract | Review question |
| --- | --- | --- |
| Explicitness | Inputs, results, failures, and completeness are stated explicitly | Which preconditions must callers still guess? |
| Strong references | Search yields candidates; operations bind to declaration identity and source revision | Does the operation still select the right declaration after an overload, duplicate name, or edit? |
| Context efficiency | Return the smallest sufficient context by default; expand on demand and mark truncation | Does this information help the current decision? |
| Single source of truth | Types, references, documentation, and diagnostics derive from compiler facts | Is the same fact maintained independently elsewhere? |
| Locality | Explicit boundaries support local understanding, edits, and verification | How much unrelated implementation must a local task understand? |
| Verifiability | Success conditions are checked by the compiler, tests, or explicit preconditions | What layer does the evidence actually prove? |
| Short feedback | Query and check incomplete source while retaining actionable diagnostics | How quickly is an error found, and is the feedback sufficient to fix it? |
| State consistency | Queries, edits, and verification identify their respective inputs | Which revision of the code produced this result? |

Measure token use and latency alongside task success. Omitting required information, hiding errors, or presenting a local check as complete verification is not an optimization.

## Shared architecture

Queries and refactors belong to `language`, project snapshots and scheduling to `workspace`, and compilation and test execution to `application`. CLI, LSP, and a future MCP integration only translate protocols; they must not implement a second name resolver, type inference engine, or refactoring rule set. Qualified names are the primary CLI selection mechanism and resolve through semantic ownership to internal declaration identity. Ambiguity must return copyable candidates. Queries use one query entry point and expand related information through options. Each refactor type shares input revision, edit set, and preflight result, with preview as the default. Command help must be enough to invoke it without learning a JSON input-file format. See the [compiler architecture](/spec/compiler-design) for source and identity boundaries and the [toolchain development guide](/design/toolchain-development) for dependency constraints.

Authoring operations use `DocumentId`, `SymbolId`, and revisions; Core content identity serves dependency and cache purposes. Declaration identity, source revision, and character position are not interchangeable. Machine output uses an explicit protocol model rather than serializing the compiler's entire object graph.

## Capability boundaries

### Checking and testing

Checking verifies source without executing the business entry point. Module configuration evaluation and dependency resolution still follow normal project-loading rules. Machine output distinguishes input, compilation, execution, test, and infrastructure failures and preserves exit codes. Program logs are separate from structured results. Finding no tests must not report test success.

Diagnostic locations specify character encoding and coordinate basis. Error codes, related locations, and notes preserve their original facts. Typed error details come from the diagnostic producer, not from parsing human-readable messages backward.

### Queries

Project overview, symbol, and contextual queries consume the same semantic snapshot. Search results may be ambiguous; operation entry points require an exact selection. By default, provide signatures, provenance, and necessary relations, expanding source on demand; pagination and truncation must be explicit. Even with erroneous source, return determinable facts and diagnostics.

### Edits

Generate an edit plan, preflight it on a source overlay, and apply it against the required prior revision. Preflight reports diagnostics before and after an edit, allowing repair of an already broken project. The first scope covers existing semantic rename; refactors such as signature changes each need complete semantic support. Cross-file application must define failure recovery separately. Atomic writes per file are not an atomic transaction across the whole edit.

### Verification scope

Test association and static dependencies can guide selection but cannot establish complete dynamic coverage. Additions, deletions, and renames require comparison of the models before and after. Resource, configuration, and Java interoperability changes must state the analysis boundary and broaden verification according to the actual impact.

## Delivery order and acceptance

First establish structured CLI checking and test feedback, then add project, symbol, and contextual queries and rename preflight. The agent learning entry reuses the specification, generated API reference, and executable documentation examples. MCP integrates after the shared contract stabilizes.

Acceptance tasks cover feature addition, type-error repair, cross-file rename, API changes, and test additions; preparation and execution are described in the [agent task benchmark](/tooling/agent-benchmark). Fixed tasks and independent behavioral acceptance record success rate, repair rounds, context use, and elapsed time. Tool contract tests do not substitute for measurement on real agent tasks. Do not claim performance or success-rate improvements without that measurement.
