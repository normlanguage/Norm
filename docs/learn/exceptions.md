# 66 Exception handling

`throw` interrupts normal execution; the matching `catch Exception error` handles it, and the program continues after the try statement.

<<< ../../norm/tests/docs/tour/exceptions.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/exceptions.out{text}

Try it: Remove the catch and observe the unhandled exception.

Precise rules: [Reference](/spec/error-model).
