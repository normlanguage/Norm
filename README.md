# Norm

Norm is a pre-design repository for a statically typed, application-oriented programming language.

Norm prioritizes explicit behavior, predictable semantics, strong typing, value semantics by default, and practical application development.

## Status

**Pre-design / specification draft.** The compiler has not been implemented yet.

## Documentation

The complete handbook lives in `docs/` and is built with VitePress.

After GitHub Pages deployment, the documentation is available at:

**https://w0fv1.github.io/norm/**

## Planned repository layout

```text
compiler/   frontend, type checker and IR
runtime/    Norm runtime and GC integration
truffle/    first execution backend
stdlib/     standard library
cli/        norm command-line tools
docs/       language handbook
```

## Execution strategy

Norm's first execution backend is planned around GraalVM/Truffle. The language frontend and typed IR are deliberately kept independent so that a dedicated native backend can be added later without redefining the language.
