# 75 Interceptor entry and exit

`before` runs before the function body and `after` runs when the invocation completes. `completion.succeeded()` reports whether the intercepted call completed normally.

<<< ../../norm/tests/docs/tour/interceptor_order.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/interceptor_order.out{text}

Try it: Make `greet` throw and observe the completion state.

Precise rules: [Reference](/spec/annotations).
