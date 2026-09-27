---
title: Semantic Refactoring Preview
description: Qualified-name selection and a shared refactoring preview
---

# Semantic Refactoring Preview

Each refactoring uses a subcommand and the same preview contract. See the help for complete arguments:

```bash
norm refactor -h
norm refactor name -h
norm refactor name path/to/module app.orders.amount --to total --preview
```

The current `name` type performs semantic renaming. See [Semantic Query](/tooling/semantic-query) for qualified-name and ambiguity-selection rules. `--to` is required. The default behavior equals explicit `--preview`: it generates edits and static validation without modifying disk files. `--apply` is not yet available.

Invalid names, ambiguous selections, same-scope naming conflicts, and read-only generated sources cause failure. Name resolution binds declaration identity and document revisions captured by this invocation; callers need not supply identities or hashes. Named arguments are linked to parameter declarations through resolved calls, so a parameter rename also changes those call labels.

## Preview and validation

`refactor.inputs` in the result identifies captured source-text revisions. For each changed file, `changes` provides the URI, before/after revisions, and all replacements. Replacements use zero-based UTF-16 offsets with exclusive ends and are ordered from later to earlier positions in the original text; `oldText` and `newText` identify replaced and new text.

Validation statically analyzes a source overlay for the same `CompilationRequest`, without rescanning files or running the program. `beforeDiagnostics` describes the original sources; top-level `diagnostics` describes the preview. Existing errors do not prevent preview generation. If compile errors remain after the preview, the result has a compile-failure status while retaining the edit plan and both sets of diagnostics; it must not be presented as a directly deliverable edit.

Document revisions cover source text only. Module configuration evaluation, dependency resolution, and compile scope come from the project load in this invocation; the input list is not a full snapshot of external resources and Java artifacts. A passing preview proves static constraints only for those captured inputs, not runtime behavior or subsequently changed files.

## Applying edits

Before applying a preview, a caller should verify captured source revisions and check that each replacement range still matches `oldText`. Obtain a new preview after content changes. Recheck and run relevant tests after editing. The CLI currently offers no disk-application transaction; the editing client is responsible for recovery from cross-file write failures.

The single implementation entry points for fields and semantics are [RefactorPreview](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/RefactorPreview.java), [LanguageService](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/language/LanguageService.java), and [SemanticQueryWriter](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/cli/component/SemanticQueryWriter.java). [RenamePreviewTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/language/RenamePreviewTest.java) covers input capture, cross-file edits, and validation with preexisting errors.
