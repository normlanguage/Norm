---
title: Agent Development
description: Norm specifications, semantic tools, and verification for AI Agents
---

# Agent Development

[Norm-Skill](https://github.com/normlanguage/Norm-Skill) provides a task index for CLI usage, language rules, and library inventory. The sources are maintained through the `cli/norm-skill` submodule; obtain them with `git submodule update --init cli/norm-skill`. Skill content is maintained only in its separate repository.

Start with the installed `norm -h` for CLI usage. Each command has `norm <command> -h`; refactoring types use `norm refactor name -h`. Help does not execute project operations.

Use language and APIs supported by the current toolchain. See [Status](/status) for capability maturity and [Agent Tooling Design](/design/agent-tooling) for principles and plans. A capability in a source checkout does not necessarily exist in an official release.

## Learning and querying

[Semantic Query](/tooling/semantic-query) provides project overviews, declaration search, strong reference selection, and context.

[Semantic Refactoring Preview](/tooling/rename-preview) provides edit previews and static validation tied to document revisions.

- [Language Tour](/learn/): sequential learning with runnable examples;
- [Language Reference](/spec/language-spec): exact syntax and semantics;
- [Design Principles](/guide/design-principles): type, information, and layering boundaries;
- [Standard Library](/stdlib/overview): current module entry points;
- [API Documentation Export](/tooling/api-documentation): public declarations and docs from the semantic model;
- [VS Code](/guide/vscode): completion, signatures, navigation, references, and renaming.

For type relationships, first consult [value, class, and ref semantics](/spec/value-identity-semantics), [nullability and inference](/learn/nullability-inference), [functions](/learn/functions), and [error handling](/learn/errors). Do not infer Norm behavior from other languages.

## Verification

See [Checks and Tests](/tooling/verification) for static checks, focused tests, and machine output. See the [Testing API](/stdlib/testing-api) for test declarations and associations. The [Norm test index](https://github.com/normlanguage/Norm/blob/main/norm/tests/README.md) describes project-local runnable examples and their verification commands.

A verification claim must state what actually ran. Successful compilation proves static constraints only; passing tests prove only their executed assertions. Revalidate the corresponding sources after changes.

See [Agent Task Benchmark](/tooling/agent-benchmark) for reusable task preparation, independent acceptance, and measurement boundaries.
