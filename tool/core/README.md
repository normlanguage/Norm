# Core

The Java implementation of the Norm frontend, semantic model, bound representation, canonical Core IR, and definition storage. Runtime composition lives in `execution-api`; GraalVM/Truffle execution lives in `truffle-backend`.

Shared compiler/backend contracts live in `dev.w0fv1.norm.abi`. The semantic layer depends only on syntax, values, diagnostics, and ABI contracts; builtin catalogs consume semantic types through `BuiltinSemanticIndex` and do not leak catalog ownership back into the semantic model.
