# Filesystem

The filesystem module provides path values, file byte streams, and bounded text reads. `Path` is a pure value; `FileReader` and `FileWriter` are external resources that must be closed.

A relative Path is based on the working directory of the current execution platform. The CLI captures the process working directory when execution starts; embedders and tests explicitly inject the base directory through the platform adapter.

```norm
Path path = Path(value: "data/settings.json")
String text = readText(
    path: path,
    encoding: TextEncoding.Utf8,
    maximumBytes: 1048576
)
```

## Errors

System failures such as missing files, insufficient permissions, existing files, and wrong path types throw `FileException`. File-operation failure is not represented by null, a status code, or Result.

## Writing

`openWrite(path:, mode:)` distinguishes `CreateNew`, `Replace`, and `Append`. `flush()` advances userspace buffers; `sync(mode:)` distinguishes data-only synchronization from data-and-metadata synchronization.

`writeTextAtomic(path:, text:, encoding:)` creates parent directories, writes and synchronizes complete content in a same-directory temporary file, then atomically replaces the target. If the filesystem cannot replace atomically, it throws `FileException` instead of truncating and rewriting the target. This publishes complete file content but does not provide compare-and-swap semantics for concurrent modifications.

[`std.filesystem.files`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/filesystem/files.norm) defines the complete signatures.
