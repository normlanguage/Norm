---
title: 'Block Call Chains: Design and Delivery'
description: Dotless continuation of ordinary member calls, migration boundaries, and verification entries
---

# Block Call Chains: Design and Delivery

| Item | Detail |
| --- | --- |
| Design status | implemented |
| Language version | Norm 0.23.0 |
| Language rule | [Advanced function rules: block call chains](../spec/grammar/functions-advanced.md#block-call-chains) |
| Nature of change | Call-syntax extension; no additional Task execution mechanism or two-callback standard-library API |

## Decision

Adopt ordinary member-call continuation after a trailing-closure call. Its sole meaning is sequential calls: each segment receives the preceding segment's return value. Do not add multiple callback arguments, general infix expressions, a pipeline operator, a Task-specific AST, a keyword allowlist, automatic safe calls, or task expansion. Syntax eligibility does not depend on type lookup and does not guess whether a statement is independent from capitalization or API names.

The [advanced function rules](../spec/grammar/functions-advanced.md#block-call-chains) are the single normative entry. See [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm) and the [concurrency API](../stdlib/concurrency.md) for Task declarations and execution contracts.

## Implementation boundaries

| Location | Responsibility and constraint |
| --- | --- |
| [BlockCallChainSyntax](../../cli/compiler/src/main/java/dev/w0fv1/norm/syntax/BlockCallChainSyntax.java) | Neutral token and same-line test shared by parsing, continuation fingerprints, and completion; no frontend, semantic, or UI dependency. |
| [Parser](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/Parser.java) | Verify the real predecessor and control-structure depth in the same postfix loop. On failure consume no token; on success create an ordinary Member and reuse trailing-lambda logic. |
| [Syntax](../../cli/compiler/src/main/java/dev/w0fv1/norm/syntax/Syntax.java) | Reuse Member, Call, and CallArgument. Each call still has at most one trailing lambda; add no chain node, Bound IR, or Core opcode. |
| [IncrementalAnalysisPlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/IncrementalAnalysisPlan.java) | Token fingerprints record conservative continuation-candidate markers, not absolute line numbers or all whitespace. Invalidate a declaration and its dependents when continuation eligibility changes. This shares a correctness boundary with Parser. |
| [CompilerSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilerSession.java) | Captured source caches continue using exact text comparison. Restart a hot LSP; an updated disk JAR does not update a running process. |
| [CoreIdentityVersion](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreIdentityVersion.java) | Separate old cache identities without changing Core schema or stdlib ABI merely for syntax sugar. This identity is not a parser compatibility switch by source origin. |
| [SourceFormatter](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/SourceFormatter.java) | Choose canonical spelling for a complete Call while preserving statement boundaries and continuation heads that cannot be wrapped. Do not store source-spelling preferences. |
| [MemberAccessSite](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/MemberAccessSite.java) / [CompletionContextResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CompletionContextResolver.java) | Use the real receiver, name-replacement range, and access form. Reject cross-snapshot positions; do not fabricate dots or rewrite captured text. |
| [CompletionEngine](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CompletionEngine.java) | Explicit and dotless calls share candidates, visibility, and ranking. Prefix completion need not force an executable AST; do not insert a block that already exists. |
| [CallSiteResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/language/CallSiteResolver.java) | Obtain active callback, generic substitution, and parameter index from the real call/closure scope and ResolvedCall; do not implement overload resolution again. |

Hover, definition, references, rename, errors, and semantic highlighting for complete calls continue using real member-name ranges. TextMate does not determine call relationships or hard-code `then`, `error`, or callback parameters as language keywords.

## Source migration

An older compiler may have parsed adjacent same-line trailing closures as independent statements; the new compiler may parse them as a member chain. Compilation with the new compiler does not prove behavior is unchanged. To retain independent calls, insert a newline or semicolon mechanically.

First freeze the complete old distribution and source inventory. Use the old AST to establish statement or result-builder element boundaries, record candidates and necessary edits, and only then process them with the new compiler or formatter. Token filtering may identify candidates; repository-wide regex replacement or edits to ordinary string content are not acceptable.

Audit standard-library source, language tests, Norm documentation examples, embedded test strings, code-generation templates, actual Todo/UI source, and NARs, local modules, and generated bindings in the selected dependency closure. The inventory records origin, source digest, candidate range, prior statement relation, and disposition.

Rebuild affected first-party NARs from authoring source, assign new package versions, and update locks. Do not rewrite an archive or integrity cache under an existing coordinate. An application cannot be declared migrated while a third-party archive it uses remains unupgraded. Unaffected locked archives need no pointless rebuild. This audit is not part of ProjectLoader's normal operation.

Deliver the new release as a whole under the [language evolution rules](language-evolution.md). Do not put a partial experimental JAR on a user's PATH or in the extension. CLI, editor, and Native use one complete toolchain identity; do not build a compatibility mode that works only in CLI.

## Delivery batches

| Batch | Boundary |
| --- | --- |
| D0 | Freeze the old distribution. Audit and migrate independent blocks, first-party templates, archives, and generated inputs; retain old behavior as a baseline. |
| D1 | Add red tests for AST, boundaries, and line breaks, then deliver postfix parsing, continuation fingerprints, and semantic version together. |
| D2 | Establish canonical formatting, idempotence, and behavioral round trips without repository-wide formatting noise. |
| D3 | Deliver member locations and shared completion, signature help, navigation, recovery, and real-editor acceptance. |
| D4 | Adopt the spelling in library examples and Todo; retain the standard Task API and lifecycle without reviving ui.async. |
| D5 | Verify complete distributions, extensions, Todo EXE, staged regressions, GUI, and delivered EXE lifecycles; match source and toolchain digests. |

Review and obtain commit authorization before each batch. Intermediate artifacts must not replace the delivered toolchain. Freeze relevant source while building, testing, or accepting it; do not let multiple builds write one output directory concurrently.

## Verification index

| Scope | Executable entry |
| --- | --- |
| S01–S11: call association, types, precedence, evaluate-once behavior, diagnostics, extensions, Task, and ownership | [BlockCallChainSyntaxTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/BlockCallChainSyntaxTest.java), [BlockCallChainExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/BlockCallChainExecutionTest.java), FunctionCompilerTest, AsyncExecutionTest, TaskExecutionTest. |
| B01–B08: LF/CRLF/CR, separators, non-call braces, control structures, unsupported forms, interpolation, incomplete source | BlockCallChainSyntaxTest, SourceRecoveryTest, CompactGuiSyntaxTest. |
| I01–I04: line-edit directions, cold/hot consistency, equal names with different output, reuse across nonsemantic whitespace, undo/redo, caches | [IncrementalAnalysisPlanTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/IncrementalAnalysisPlanTest.java), IncrementalCompilationTest. |
| F01–F04: canonical spelling, independent blocks, long chains/parentheses, erroneous files | [SourceFormatterTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/SourceFormatterTest.java). |
| L01–L04: prefix edits, navigation, callback scope, signatures, and session boundaries | [BlockCallChainLanguageTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/language/BlockCallChainLanguageTest.java), LanguageServiceTest, RenamePreviewTest, LanguageServerTest, and [real VS Code tests](../../cli/extensions/vscode/src/test/extension.test.ts). |
| A01: discover and execute synchronous and Task chains | [functions programs](../../norm/tests/functions) and [discovery rules](../../norm/tests/README.md). |
| A02: cross-module/archives and generated source | Complete input inventory, locked-archive digests, and generated binding source loaded by the actual application, not merely a repository-text search. |
| A03: four stages in both Todo JVM and Native | Todo repository `reference-tests/verify.ps1`: add, edit, complete/filter, delete, clear, continuous input, and persistence across two restarts. |
| A04: delivered EXE | Isolated launch–close–restart–close of the same-digest `todo.exe`, checking title and completion-state persistence, exit code 0 for app and launcher, and no runtime exception. `check.exe` does not substitute. |
| A05–A08: lifecycle, artifact identity, everyday data, and architecture | Task/async/cancellation and cleanup tests; complete distribution and source SHA-256; unchanged digests of everyday H2 databases; DependencyArchitectureTest and AuthoringArchitectureTest. |

See the [toolchain development guide](toolchain-development.md#local-acceptance-and-measurement-entries) for local distribution and formatting checks. Toolchain selection and integrity reuse [select-toolchain.ps1](../../cli/compiler/scripts/select-toolchain.ps1), [resolve-toolchain.ps1](../../cli/compiler/scripts/resolve-toolchain.ps1), and [test-toolchain.ps1](../../cli/compiler/scripts/test-toolchain.ps1). The extension reuses `npm run test:language`, `npm run check`, focused end-to-end tests through `NORM_TEST_GREP`, and its existing packaging entry.

Use the new selected distribution from the adjacent Norm repository to run Todo `build.ps1` and `reference-tests/verify.ps1 -Mode all`, then verify the delivered EXE. Use a newly isolated database for every write. Installation, loading, and LSP restart need real process and digest evidence; displayed version strings alone are insufficient.

For each batch, retain actual commands, exit codes, case names and counts, failures/skips, raw logs/XML, source identity, and artifact digests. Merely checking that a factory exists, compilation passes, or a window opens is not behavioral acceptance. Record local Maven/NAR cache hits separately; they do not establish a cold-cache build or a different platform's release matrix. Measure performance separately rather than inferring zero overhead or speedups from syntax sugar.
