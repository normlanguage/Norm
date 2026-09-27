# Ecosystem roadmap

Ecosystem work follows the [implementation strategy](/design/implementation-strategy) and [compiler bootstrap plan](/design/bootstrap-plan). It does not introduce a second compiler or execution backend.

## Phase 1: Complete the language loop

- Java compiler frontend and type checking.
- Truffle execution backend.
- Core runtime and minimal standard library.
- Release the `norm` CLI with a bundled Java runtime.

## Phase 2: Development tools

- Formatter and Language Server.
- Package manager and package registry.
- Testing, diagnostics, and profiling tools.

## Phase 3: Application ecosystem

- Web platform and database adapters.
- Observability, deployment, and framework integration.
- A third-party package ecosystem supported by a stable language specification.

Third parties may research other implementation technologies, but the Norm project does not include LLVM, Cranelift, a custom native backend, or a Zig toolchain on its official roadmap.
