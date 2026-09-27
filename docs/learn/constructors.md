# 30 Explicit constructors

A class can replace implicit field construction with a same-named constructor.

<<< ../../norm/tests/docs/tour/constructors.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/constructors.out{text}

`Counter(Integer initial)` has no return type. The constructor assigns the required field before the instance can be used, and the call labels its `initial` parameter.

Try it: Double `initial` in the constructor and predict the field value.

Precise rules: [Reference](/spec/grammar/classes).
