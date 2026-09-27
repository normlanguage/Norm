# System Runtime Architecture

Status: **Accepted**

This document defines a unified runtime boundary for std.io, filesystem, network, http, websocket, time, process, regex, crypto, and concurrent. Each module's public API is defined by its standard library documentation; this is the sole implementation design for host integration, exception conversion, and resource lifetimes. See the [WebSocket client](../stdlib/websocket.md) for its contract and verification entry.

## Invariants

- Public system APIs express failure with catchable Norm exceptions, not `Result`.
- Normal protocol outcomes such as HTTP status, process exit status, a failed match or validation, and EOF remain ordinary values.
- Public APIs and public types expose no Java, Truffle, or concrete provider.
- Host operations enter only through standard-library internal intrinsics; applications get no second low-level API.
- External resources such as files, sockets, HTTP bodies, processes, and concurrent scopes close deterministically.
- CLI, Polyglot, and tests use the same execution-capability assembly entry.
- Core describes only already-bound intrinsic identities and does not depend on platform adapters.

## Layers

```text
norm/stdlib
  → stdlib-internal intrinsic ABI
  → truffle system bridge
  → platform contracts
  → JDK platform adapter
```

`norm/stdlib` holds public Norm types, functions, exceptions, and resource wrappers. Only standard-library source may resolve system intrinsics and opaque handle types.

The compiler's `platform` package holds backend-neutral `SystemPlatform` contracts and typed platform failures. It neither constructs Norm values nor depends on Truffle.

`platform.jdk` implements files, networking, HTTP transport, clocks, processes, regex, crypto, entropy, and scheduling. It normalizes JDK exceptions into platform-contract exceptions.

The `truffle` package holds host-value representations, resource scopes, intrinsic execution, and Norm exception construction. `GuestValueFactory` constructs exception values from the ABI and current artifact metadata. Host I/O occurs only on slow paths behind `@TruffleBoundary`.

## Execution capabilities

`ExecutionContext` holds borrowed standard streams, application arguments, environment, directories, completion status, execution controls, and a strongly typed `SystemPlatform`. The execution host injects standard streams; they are not resources created by the platform factory. Platform capabilities have a fixed composition, not a string-keyed registry or global service locator.

```text
SystemPlatform
├─ fileSystem
├─ network
├─ httpTransport
├─ webSocketTransport
├─ clock
├─ processes
├─ regexEngine
├─ cryptoProvider
├─ secureEntropy
└─ scheduler
```

A single factory in `platform.jdk` creates the default platform. Tests derive from the same factory and replace only the capability they need to control. Cancellation and deadlines belong to each execution or child task, not global platform state.

## Exception boundary

Norm system exceptions use single inheritance and domain reason enums:

```text
Exception
└─ SystemException
   ├─ IOException
   │  ├─ FileException
   │  ├─ NetworkException
   │  ├─ HttpException
   │  ├─ WebSocketException
   │  └─ ProcessException
   ├─ TimeException
   ├─ RegexException
   ├─ CryptoException
   └─ ConcurrentException
```

`SystemException` provides a stable code. Domain exceptions provide typed operation, reason, and necessary context; a reason is exception metadata, not a return branch. Machine decisions must not depend on the exception message.

Platform adapters translate expected host failures into typed platform exceptions. Truffle's `GuestValueFactory` constructs a Norm exception value from the current artifact's nominal metadata, which crosses into guest `try/catch/finally` through the existing `NormThrownException`. Unknown implementation defects and violated runtime invariants remain uncatchable stable runtime errors.

Exception identity, field ordinals, and intrinsic mappings enter the builtin ABI and are verified by compiled standard-library contract tests. Runtime types must not be guessed from display names.

## Host values

The runtime has two reusable shapes:

- `OPAQUE_VALUE` for host-backed values such as Clock, compiled Regex, and cryptographic state;
- `OPAQUE_RESOURCE` for external resources such as file streams, sockets, HTTP bodies, processes, and task scopes.

Each value still carries its complete `CoreType`. Distinct public or internal Norm types may share a shape but are not interchangeable.

An opaque value's equality, hash, and copying follow its Norm value contract. An opaque resource has identity, so ordinary assignment shares its handle. A public wrapper's shallow `copy()` shares the same internal handle and closed state.

## Resource lifetime

Each execution creates an independent `ResourceScope`. Register a resource immediately after a successful open; leave the active set after an explicit close, whether closing succeeds or fails. The execution closes remaining resources on both normal return and exception paths.

Scope cleanup is an execution boundary, not a substitute for deterministic closing in public APIs. Tests must be able to assert that resources have not leaked. Repeated close neither repeats the host operation nor changes the first close result.

Concurrent child tasks share the execution platform but hold derived cancellation contexts. Before ending, a task scope must wait for, cancel, or propagate every child task; resources cannot outlive their execution.

## Standard-library dependencies

```text
std.core
├─ std.time
├─ std.io
├─ std.regex
├─ std.concurrent ──→ std.time
├─ filesystem ──────→ std.io, std.time
├─ network ─────────→ std.io, std.time
├─ process ─────────→ std.io, filesystem, std.time
├─ crypto ──────────→ std.io
└─ http ────────────→ std.io, network, std.time, std.concurrent
```

Public system I/O is synchronous by default. Structured concurrency composes blocking operations and propagates cancellation. Host implementations may use virtual threads or asynchronous machinery without introducing a second public async API.

## Foundational types

System module development first fixes these public semantics:

- `Duration`, `Instant`, `Deadline`, and an explicit `Clock`;
- `Bytes`, `TextEncoding`, and `ReadChunk` that does not conflate EOF;
- partial reads, partial writes, and `writeAll`;
- the `Resource` close protocol and scoped-use entry;
- propagation and exceptions for cancellation and deadlines;
- rules for retaining exception causes and cleanup failures.

Each type is declared once in its standard-library source; other modules reference rather than duplicate it.

The host representation of `Bytes` is a contiguous `byte[]` plus a logical interval. Slices share read-only storage, and file reads take ownership of storage returned by the adapter. Only explicit `toArray()`, concatenation, and encoding boundaries make copies. The standard library and filesystem construct byte values through the same opaque type identity in the builtin ABI.

The file-stream adapter uses blocking `FileChannel`, exposing partial read, partial write, flush, sync, and close. Host calls sit behind `@TruffleBoundary`. The platform factory reads the working directory when assembling an execution. This path does not depend on dynamic classpath scanning or provider service loading.

## Intrinsic organization

The builtin ABI is the sole source for intrinsic identity and runtime shapes. The static entry [`IntrinsicDispatcher`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/truffle/IntrinsicDispatcher.java) exhaustively selects domain implementations without runtime registration or scanning. [`IntrinsicOperationTest`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/IntrinsicOperationTest.java) verifies the full mapping.

[`JavaValueAdapter`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/truffle/JavaValueAdapter.java) owns conversion of Java arguments, results, and callback carriers. Java class loading and call resolution remain in `jvm`, and value semantics remain in `RuntimeValues`; the adapter does not own per-execution resources.

Internal standard-library capabilities follow module bootstrap's restricted visibility through a unified access policy. New system modules must not add double-underscore globals or domain-specific exceptions to the ordinary application prelude.

## Verification

Each system capability needs:

- adapter unit tests using its real next-layer dependency;
- Truffle tests for conversion of platform exceptions into catchable Norm exceptions;
- standard-library tests in `norm/stdlib/std/tests/test`;
- tests with real temporary directories, loopback sockets, fixed clocks, or real child processes;
- real `.norm` file execution through CLI;
- pre-release comparison of development and self-contained CLI behavior.

External-network smoke programs live in `norm/tests/live` and run only manually or in the release pipeline, not in the default deterministic test suite.

## Implementation order

1. System-exception ABI and `GuestValueFactory`.
2. `SystemPlatform`, JDK platform adapter, and unified `ExecutionContext` assembly.
3. Opaque values, opaque resources, and `ResourceScope`.
4. Internal standard-library intrinsic access policy and domain registry.
5. Foundational time and I/O types.
6. First real vertical filesystem slice.
7. Concurrent, network, process, and HTTP.
8. Regex and crypto.

Keep compilation, execution, and relevant tests passing at every step; remove superseded entries before proceeding to the next.
