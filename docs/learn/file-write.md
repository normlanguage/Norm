# 85 Writing and reading a file

`CreateNew` claims a fresh filename and fails rather than overwriting an existing file. The writer is closed in `finally` before a bounded UTF-8 read returns the text.

<<< ../../norm/tests/docs/libraries/files/write_read.norm{norm}

From the Norm repository root, use a new, empty scratch directory:

```sh
mkdir .tmp/norm-file-lesson
cd .tmp/norm-file-lesson
norm run ../../norm/tests/docs/libraries/files/write_read.norm
```

Expected output:

<<< ../../norm/tests/docs/libraries/files/write_read.out{text}

The program leaves its own `note.txt` in that scratch directory for the next lesson. Repeating the write without removing it fails while opening the existing file; the file is not overwritten.

Try it: Run the command twice and inspect the second failure.

Precise rules: [Filesystem API](/stdlib/filesystem).
