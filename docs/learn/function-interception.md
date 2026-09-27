# 74 Function interception

`FunctionInterceptor` wraps the declared `greet` call. `around` calls `proceed()` once, so the original function still supplies the greeting.

<<< ../../norm/tests/docs/tour/function_interception.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/function_interception.out{text}

Try it: Remove `proceed()` and inspect how the required return value must be supplied.

Precise rules: [Reference](/spec/annotations).
