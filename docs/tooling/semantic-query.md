---
title: Semantic Query
description: Query declarations by qualified name and expand semantic facts on demand
---

# Semantic Query

```bash
norm query -h
norm query path/to/module
norm query path/to/module --search amount
norm query path/to/module app.orders.amount
norm query path/to/module app.orders.amount --source --references
norm query path/to/module "app.orders.amount(Integer).value"
```

Queries always output JSON. With no selector, the result contains a project overview and declaration list. `--search` searches declaration names; a positional argument selects an exact qualified name. Checks and queries share project loading and do not run a business entry point. Even when sources contain errors, determinable query results and diagnostics are retained.

## Qualified names

A qualified name derives from the source package and semantic ownership, such as `app.Order`, `app.Order.code`, or `app.orders.amount.value`. Without a package declaration, it starts at the top-level name. Parameters, type parameters, and local variables can be selected exactly, although default search excludes these internal declarations and `self`.

Search results provide `qualifiedName`, `selector`, and `at`. Overloads use a selector containing parameter types, such as `app.orders.amount(Integer)`. Internal declarations continue from the owning function selector, such as `app.orders.amount(Integer).value`. Quote arguments containing parentheses or spaces in the shell; copying a returned selector is preferred.

If several declarations match, the result is `input_error` with `query.candidates`, not an automatic first choice. If locals with the same name or overloads displaying the same type remain ambiguous, add `--at "<uri>#<offset>"` by copying a candidate's `at` field. The position is the zero-based UTF-16 offset at the declaration start.

Qualified names resolve against the current sources each time. Returned `id` and `revision` identify facts in this invocation; callers need not construct identity or digest inputs. Names and positions are not persistent identities across edits.

## On-demand expansion

An exact query returns only declaration kind, signature, type contract, source, and documentation by default. The following options can be combined:

| Option | Content |
| --- | --- |
| `--source` | Smallest declaration source range containing the selection |
| `--references` | Semantic reference locations |
| `--dependencies` | Declaration dependencies |
| `--tests` | Explicitly associated tests |

A parameter or local variable may use the source range of its owning function. Unrequested related data is neither calculated nor returned. There is no separate context command.

`--offset` defaults to 0; `--limit` defaults to 20 and accepts 1 through 1000. Search, ambiguity candidates, and each expansion group separately return `total`, `offset`, and `hasMore`. Expansion groups share pagination arguments but have independent totals. Calls across pages do not promise one snapshot; query again after source changes.

Associated tests are not a complete affected-test set, and declaration dependencies do not cover all dynamic behavior. External declarations support exact signature queries; `contextAvailable` indicates whether source context can be expanded. Source locations use zero-based, half-open UTF-16 ranges. A document revision covers only the captured text's UTF-8 SHA-256, not a complete project snapshot containing configuration, resources, and Java artifacts.

## Implementation and verification entry points

- [SemanticQuery](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/SemanticQuery.java): queries, selection, and expansion;
- [DeclarationNames](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/DeclarationNames.java): names and overload selectors;
- [AuthoringCommand](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/controller/AuthoringCommand.java): layered help and CLI;
- [QualifiedAuthoringTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/cli/controller/QualifiedAuthoringTest.java): real-source command acceptance.

See [Checks and Tests](/tooling/verification) for shared diagnostics and failure structure.
