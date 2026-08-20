# Norm File System Library

The file system API provides Path, File, Directory and stream operations.

Principles:
- explicit resources
- GC manages memory, not external resources
- errors use Result for expected failures
- exceptions for unexpected system failures

Example:
```norm
Result<String, FileError> text = File.readText(path = path)
```
