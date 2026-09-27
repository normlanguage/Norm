# 09 Default arguments

A parameter with a default supplies a value when its caller omits that label.

<<< ../../norm/tests/docs/tour/default_arguments.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/default_arguments.out{text}

`punctuation` defaults to `"!"` in the first call. The second call explicitly supplies `"?"`. The body sees a `String` either way; the default is part of the function declaration's call contract.

Try it: Change the default to `"."` and observe which call changes.

Precise rules: [Language Reference](/spec/grammar/functions).
