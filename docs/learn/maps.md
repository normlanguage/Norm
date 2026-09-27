# 19 Maps

A map associates a typed key with a typed value.

<<< ../../norm/tests/docs/tour/maps.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/maps.out{text}

`put` writes a key-value pair. The index read retrieves Ada's value, and `containsKey` checks another key. Writing Ada again replaces its value without increasing the entry count.

Try it: Write a new value for Ada and observe whether the entry count grows.

Precise rules: [Reference](/stdlib/map).
