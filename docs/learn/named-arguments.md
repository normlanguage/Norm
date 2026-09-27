# 08 Named arguments

Calls with multiple arguments name the parameter receiving each value.

<<< ../../norm/tests/docs/tour/named_arguments.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/named_arguments.out{text}

`left: 9, right: 4` computes `9 - 4`. The second call reverses the written order while preserving each label's value, so it still produces `5`. The third call changes the value bound to each label and produces `-5`. Labels determine parameter binding; source order determines when the argument expressions are evaluated.

Try it: Give each argument a function call that prints a line, then observe evaluation order.

Precise rules: [Language Reference](/spec/grammar/functions).
