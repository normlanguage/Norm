---
title: Sample System
description: Ownership, teaching progression, and acceptance for Norm samples
---

# Sample System

Norm has five sample surfaces. Each has one source owner and a distinct reader task.

| Surface | Reader task | Source owner |
| --- | --- | --- |
| Website | Decide why to use Norm | Executable showcase files in the main repository |
| `norm hello` | Explore five complete programs locally | One CLI template source in the main repository |
| Language | Examine precise behavior and combinations of language features | Professional examples in the main repository |
| Learn | Acquire one feature at a time | Independent teaching examples in the main repository |
| Libraries | Use an actual API and solve a typical problem | `samples/` in each library repository |

English and Chinese pages share code. A page displays its maintained source file; it does not copy the code into a second example. Language reference pages may include professional language examples. Learning pages use their own smaller examples and do not include professional example source. Library usage may appear in language and learning examples when it clarifies the current language feature; those examples link to the library's `samples/` for complete API usage.

## Website

The showcase has three entry points:

1. **Simple:** the minimal `Hello, Norm` program shown directly as source.
2. **Agent first:** declaration intent from `@Document`, semantic query and related declarations, on-demand source and tests, rename preview, an agent-applied edit, then structured check and test results.
3. **Application development:** focused, switchable examples covering data models, expression results, readable calls, field responsiveness, typed behavior, and retained type information.

Each example shows an observable result, change, or diagnostic. `@Document.description` conveys intent and usage constraints; the compiler provides signatures and types. Its `types`, `functions`, and `fields` entries use checked declaration references. `@Document` is binary-retained metadata, not the runtime-annotation example. `norm docs` derives structured API documentation from the same semantic model; `--strict` checks documentation coverage. Associated tests come from test-side `@Test` declarations, not an `@Document` field. The site must confirm the showcased commands against the public release assets before presenting them as released capabilities. No agent productivity claim is made without measurement. See [Agent Tool Design](/design/agent-tooling), [API Documentation](/tooling/api-documentation), and [Annotations](/spec/annotations).

## `norm hello`

The command writes five independently runnable, handwritten single-file programs to the current directory:

| File | Program |
| --- | --- |
| `hell.norm` | Hello World |
| `sort.norm` | Bubble sort and collection value semantics |
| `maze.norm` | Breadth-first shortest path through a maze, drawn in the terminal |
| `todo.norm` | GUI Todo with add, complete, delete, filter, and local persistence |
| `board.norm` | Web guestbook with list, submit, delete, and persistence through server-rendered pages and forms |

The progression starts in the terminal and advances to GUI and Web. Dependencies use the normal toolchain; runtime data lives separately from source. The default experience needs no account, secret, or external service. Generation must not silently overwrite existing names. The template is maintained once; generated files belong to the user. English and Chinese instructions accompany the command. GUI and Web single-file dependency loading, launch, interaction, and persistence require acceptance with the actual generated files.

## Professional language examples

These examples serve experienced developers and language reference pages. A typical example is 40–60 lines and may combine related features to explain their semantics and practical use. Include selected negative examples whose expected diagnostics are explicit and checked separately. Examples may call standard or ecosystem libraries, while complete API scenarios remain with the library. Feature areas include values and identity, expression results and matching, named calls, generics and runtime types, field observation, typed annotations, declaration references, result builders, and `@Document` with semantic queries and test associations.

## Learning examples

Learning is a sequence of feature sections, grouped for navigation. Each section introduces one new feature through an independently runnable 10–30 line example, expected result, careful explanation, and one small exercise. Length is a readability target. Multi-file examples are reserved for file-boundary concepts. A later section may reuse explained constructs; it must not quietly require an unexplained construct. The learning code is independent of professional language examples, even when both explain the same feature.

The progression is:

1. Program entry, bindings, inference, interpolation, operators, conditional execution.
2. Functions, named and default arguments, argument shorthand, `if` results, final-expression returns.
3. Lists, iteration, conditional loops, `break`, `continue`, arrays, maps, sets, collection value semantics, and collection-literal `for`, `if`, and spread elements.
4. `value`, equality, `class`, identity, copying, constructors, computed properties, fluent methods.
5. Nullability, narrowing, safe access, fallback, enums, data enums, switch results, exhaustive and nested patterns.
6. Interfaces, default implementations, inheritance, overrides, generic types and functions, inference, retained generic matching, overloads.
7. Function values, lambdas, capture, method references, extensions, trailing and named callbacks, block call chains.
8. Imports, modules, packages, visibility, dependencies, tests, exceptions, cleanup.
9. Declaration references, annotations and retention, runtime annotations, interceptors, then a dedicated `@Document` section.
10. Lexical `ref` and its lifetime, field handles, subscriptions, collection changes, ownership and cleanup, result builders.

Each comma-separated feature above receives its own section where a distinct learning step is needed. Library micro-lessons follow their prerequisites: serialization after data modeling, file or request failures after error handling, GUI interaction after callbacks and state, and Web and storage after modules and dependencies. These are short teaching programs, not copies of library scenarios. The first eight stages establish the main path; the last two are advanced.

Value-position `for` results belong to the [planned loop design](/spec/grammar/loops#for-expressions), so they enter the learning path only after the compiler accepts them.

## Library `samples/`

Every library repository owns a `samples/` index. `samples/README.md` is the English entry and `samples/README.zh-CN.md` the Chinese entry; both point to the same code. At least one meaningful first-use example demonstrates a real call and result, typically in 15–30 lines. Core libraries add typical scenarios, usually 40–80 lines and split into a directory when multiple files are necessary. Each entry states purpose, preparation, run command, observable result, and API reference. Internal and aggregation repositories use their index to point to the actual user-facing owner when a new program would be artificial.

GUI library samples explain controls and binding; Web library samples explain routes and forms. The complete Todo and guestbook remain owned by `norm hello`. Public examples resolve released dependencies, while candidate-release acceptance resolves the same source through the normal dependency mechanism. Samples needing external credentials state their prerequisites, and an unrun scenario is reported as unrun. The website indexes library samples by use case without copying their code.

## Source layout and acceptance

Keep the current executable documentation path: learning pages include files from `norm/tests/docs/tour/` and `norm/tests/docs/projects/`, while the existing output and project suites compare their actual output with `.out` or `expected.out`. Refine that structure into one file per learning section. Place professional examples under a separate `norm/tests/docs/language/` suite; keep showcase and `norm hello` templates separate by ownership. Extend the current test infrastructure and VitePress file inclusion instead of creating a second sample runner or catalog format. The page navigation is the catalog.

Verify the result each example promises: output, expected diagnostic, data mutation, HTTP response, GUI interaction, or persisted state. In particular, test all five generated `norm hello` files as generated, not only their template sources. Compile success or process exit alone is insufficient when the example promises behavior.

Remove superseded public examples and their duplicate references as replacement surfaces become executable. Preserve compiler and library regression tests whose purpose is independent of public teaching. Remote repository deletion is a separate repository decision; removing old public examples does not imply deleting a repository.
