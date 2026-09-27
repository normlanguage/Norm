# I/O Fundamentals

`std.io` contains the byte, encoding, read-state, and resource protocols shared by files, networking, HTTP, and processes. System failures throw `IOException` or a more specific domain exception.

## Bytes

`Bytes` is an immutable sequence of byte values in 0..255. `bytes(values:)` is its public constructor; an out-of-range element throws `ByteException` with a stable code, element position, and value. `slice(start:, length:)` creates a read-only view sharing the underlying storage.

```norm
Bytes content = bytes(values: [78, 111, 114, 109])
Integer first = content.at(index: 0)
Bytes tail = content.slice(start: 1, length: 3)
```

`toArray()` returns a logically independent array. [`std.io.bytes`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/bytes.norm) defines the complete declarations.

## Reading and closing

`ReadChunk.Data(Bytes)` means data was read, while `ReadChunk.Eof` means end of stream. Partial reads and writes are normal results. `readAll(reader:, maximumBytes:)` reads bounded content and `writeAll(writer:, content:)` completes a write; lack of progress, invalid progress, and exceeding limits throw `StreamException`.

External resources implement `Resource.close()`. `use(resource:, body:)` closes on both success and failure, preserving the body exception as primary when both body and close fail. The execution scope cleans up resources left open.

Java Binding projects returned `java.io.InputStream` and `java.io.OutputStream` as `InputStream` and `OutputStream`. Both implement the byte protocols and `Resource` above, and retain the same host object when passed back to a JAR API.

## Text encoding

`encodeText(text:, encoding:)` and `decodeText(content:, encoding:)` provide strict UTF-8 conversion. Invalid input throws `TextException`.

See [`std.io.bytes`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/bytes.norm), [`std.io.system`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/system.norm), and [`std.io.streams`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/streams.norm) for complete declarations.

See [Command Line and Processes](/stdlib/cli) for standard input, output, and error.
