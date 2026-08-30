# Compiler

The single Java module contains the Norm frontend, semantic model, canonical Core IR, execution runtime, project lifecycle, host platform integration, CLI, and Language Server. Package boundaries and architecture tests preserve the internal dependency direction.

Shared compiler/backend contracts live in `dev.w0fv1.norm.abi`. The semantic layer depends only on syntax, values, diagnostics, and ABI contracts; builtin catalogs consume semantic types through `BuiltinSemanticIndex` and do not leak catalog ownership back into the semantic model.

The `norm` entry point provides help, version reporting, `run`, documentation export, and the stdio `lsp` server. Protocol traffic is the only standard output in LSP mode; editor adapters consume the same compiler diagnostics and language services.
