# Standard Library

The current standard library builds strongly typed APIs around core values, Unicode text, collections, controlled system resources, and structured data. The `norm/stdlib/std` sources, built-in ABI, and current-version acceptance programs define the public capabilities.

## Delivered modules

| Package | Responsibility | Reference |
| --- | --- | --- |
| `std.core` | Result, Unit, Exception, and core interfaces | [Core types](https://github.com/normlanguage/Norm/tree/main/norm/stdlib/std/core) |
| `std.annotation` | Annotation targets, retention, and interception interfaces | [Annotation specification](/spec/annotations) |
| `std.text` | Unicode normalization and text construction | [String](/stdlib/string) |
| `std.collections` | Sequence algorithms and collection extensions | [Collections](/stdlib/collections) |
| `std.configuration` | Structural mapping from typed framework configuration to host properties | [Configuration](/stdlib/configuration) |
| `std.math` | Integer math functions | [Math](/stdlib/math) |
| `std.cli` | Long options, positional arguments, and help | [CLI](/stdlib/cli) |
| `std.application` | Arguments, environment, directories, and completion codes | [CLI](/stdlib/cli) |
| `std.process` | Child processes, output budgets, timeouts, and cancellation | [CLI](/stdlib/cli) |
| `std.time` | Instant, Duration, and Clock | [Time](/stdlib/time) |
| `std.concurrent` | Typed tasks, waiting, and cancellation | [Concurrency](/stdlib/concurrency) |
| `std.build` | Strongly typed result builders | [Result builders](/spec/grammar/result-builders) |
| `std.io` | Bytes, UTF-8, streams, and Resource | [I/O](/stdlib/io) |
| `std.filesystem` | Streaming file I/O | [Filesystem](/stdlib/filesystem) |
| `std.http` | URIs, requests, responses, and HTTP clients | [HTTP](/stdlib/http) |
| `std.websocket` | WS/WSS clients, messages, and connection lifecycle | [WebSocket](/stdlib/websocket) |
| `std.serialization` | Structural mapping contracts and metadata | [Serialization](/stdlib/serialization) |
| `std.json` | JSON tree, parsing/writing, and structural mapping | [JSON](/stdlib/json-api) |
| `std.xml` | XML structural mapping | [XML](/stdlib/xml-api) |
| `std.yaml` | YAML structural mapping | [YAML](/stdlib/yaml-api) |
| `std.validation` | Field and parameter constraints | [Validation](/stdlib/validation-api) |
| `std.testing` | Assertion values and acceptance output protocol | [Testing](/stdlib/testing-api) |

`Array`, `List`, `Map`, `Set`, `Stack`, `Queue`, `Deque`, `Pair`, `Range`, and `StringBuilder` belong to the current built-in type model; standard-library sources supply composition algorithms for them.

## Shared rules

- Public APIs retain complete static types and reject raw collections.
- Ordinary absence uses `T?`; mutually exclusive business outcomes use an enum or `Result<T, E>`; system failures throw domain exceptions.
- External resources close deterministically through `Resource` and `use`.
- Text APIs distinguish bytes, Unicode code points, and graphemes explicitly.
- Serialization reads values by Core field ordinal, without JVM reflection or string-based getters.
- Configuration mapping shares structural metadata with serialization instead of maintaining a second string-property table.
- Each format retains its own domain rules and failure types rather than using a misleading unified error model.

## Current boundaries

Structural mapping handles only `value` types. Class identity, object graphs, cycles, and polymorphism are not yet part of the protocol. HTTP server applications use independent libraries; see the [library samples](/stdlib/samples) and [Status](/status) for current availability.

The Norm sources linked from each page define exact signatures. These pages explain module responsibilities, failure boundaries, and minimal usage without duplicating complete method lists.

## Independent libraries

The [OpenAI client](https://github.com/normlanguage/openai) is an independent library providing the Responses API; it is not part of the standard library.

The [library sample index](/stdlib/samples) points to runnable use cases in their owning repositories.
