# 38 Data enums

Enum alternatives may carry differently typed data.

<<< ../../norm/tests/docs/tour/data_enums.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/data_enums.out{text}

`Success` carries an integer count, while `Failure` carries a string reason. Construction names the payload field, and each value retains which variant was constructed.

Try it: Create a second `Success` with a different count and compare it.

Precise rules: [Reference](/spec/enum-design).
