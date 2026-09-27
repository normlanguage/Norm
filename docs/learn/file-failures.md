# 86 Typed file failures

After the previous lesson has claimed `note.txt`, remove only that file. A read of the same path now throws `FileException`; its typed `reason` identifies `NotFound`.

<<< ../../norm/tests/docs/libraries/files/missing.norm{norm}

If you are still in the scratch directory from lesson 85, run:

```sh
rm note.txt
norm run ../../norm/tests/docs/libraries/files/missing.norm
cd ../..
```

You can also run the source independently from any new empty scratch directory. Never remove a file you did not create for this lesson.

Expected output:

<<< ../../norm/tests/docs/libraries/files/missing.out{text}

Try it: Run the read before removing `note.txt` and observe that no exception branch executes.

Precise rules: [Filesystem failures](/stdlib/filesystem#errors).
