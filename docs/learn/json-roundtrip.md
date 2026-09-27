# 84 JSON value roundtrip

A `@Serializable` value has a checked JSON shape. `toJson` encodes its fields; `fromJson<Note>` decodes back to the declared type rather than leaving a dynamic tree.

<<< ../../norm/tests/docs/tour/json_roundtrip.norm{norm}

Expected output:

<<< ../../norm/tests/docs/tour/json_roundtrip.out{text}

Try it: Change `priority` to 3 and compare the encoded and decoded values.

Precise rules: [JSON API](/stdlib/json-api).
