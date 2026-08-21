# CLI

The Java command-line entry point for Norm. It provides deterministic help, version output, usage errors, `norm run <file.norm>`, and `norm lsp`.

`norm lsp` runs the editor-neutral language server over stdio. It exposes compiler diagnostics, completion, snippets, and hover information to the VS Code adapter and future LSP-compatible editors. Protocol traffic is the only stdout output in this mode.
