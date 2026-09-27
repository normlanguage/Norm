# Serialization Runtime Plan

## Goal

Establish a format-independent structured serialization runtime. Types explicitly join the serialization contract through runtime annotations. Field structure comes from Core metadata; JSON, XML, and YAML share one structural description, field-access mechanism, and construction path.

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String body = user.toJson()
User decoded = body.fromJson<User>()
```

## Fixed decisions

- Do not introduce macros, a compile-time derivation DSL, runtime code injection, or classpath scanning.
- Extension functions are static-call syntax for ordinary top-level functions. Real methods take precedence, candidates follow explicit imports and ordinary overload rules, and ambiguity is a compile-time error.
- `@Serializable` explicitly marks a contract; it does not generate methods. `@SerialName` and `@SerialIgnore` provide only metadata.
- Core aggregates, field ordinals, reified `CoreType`, and runtime annotations are the sole source for structure. Do not use JVM reflection or call fields or methods by string name.
- The first version automatically handles only `value`. Class identity, object graphs, cycles, and polymorphism need a separate protocol before entering automatic serialization.
- System and data-format failures throw typed exceptions with paths and locations, rather than using `Result`.

## Language and annotation foundations

An extension function uses the following declaration form, with the receiver as its first parameter:

```norm
public extension String toJson<T>(T value) {
  return encodeJson(value: value)
}
```

After binding, `value.toJson()` is equivalent to `toJson(value: value)`. An extension does not become part of the owner type or its dynamic dispatch table and cannot be implicitly imported.

Annotation applicability and interception lifecycle are distinct. `TypeTarget`, `FieldTarget`, `FunctionTarget`, and `ParameterTarget` describe targets; only `FunctionInterceptor`, `FieldInterceptor<T>`, and `ParameterInterceptor<T>` carry lifecycles. Serialization metadata and validation interceptors thus share the target model without sharing their behavior mechanism.

## Runtime structure

`Class<T>.fields()` exposes `List<Field<T, ?>>`. Each field reference retains stable identity, owner, field type, runtime annotations, and typed read capability. The public reflection API and serialization runtime both use Core aggregates and field metadata as their structural source of truth. The serialization hot path directly reads `ObjectValue.fields` by internal ordinal.

Build one serialization shape for each exact reified type:

- aggregate identity and construction plan;
- field ordinal, field type, serialized name, ignore policy, and nullable information;
- encoded field order and decoded-name index.

The structural runtime caches shapes by exact `CoreType`, closing recursive shapes through lazy references. `MapperEngine` separately caches immutable reader and writer plans by `(format, exact type)` and holds no global lock outside plan lookup. Format adapters traverse the plan directly; decoding constructs the target value through its canonical constructor. A `JsonValue` is built only when the JSON tree API is explicitly called.

The public entries are `DataMapper`, `DataReader<T>`, and `DataWriter<T>`. Format implementations handle only tokens and format metadata, rather than duplicating type discovery, field access, or object construction.

## `std.json`

The first version provides:

- `JsonValue`: object, array, string, number, boolean, and null;
- `parseJson`, `writeJson`, `toJsonValue`, `toJson`, `toJsonBytes`, and `fromJson<T>`;
- `JsonException` with stable code, value path, byte offset, line, and column;
- limits on nesting, input bytes, string length, and collection element count.

Built-in shapes cover String, Boolean, Integer, Long, nullable values, Array/List, `Map<String, T>`, enums, and nested `@Serializable value` types. Non-string map keys, non-finite floating-point numbers, cycles, and unmarked aggregates are explicitly rejected.

JSON tokenization and output use the Jackson Core streaming API. The third-party implementation is contained within the format adapter and does not participate in Norm type discovery or object construction.

## `std.yaml`

YAML and JSON reuse the Jackson token-mapping core, but YAML has its own factory, format policy, and exception ABI. YAML accepts a single document, string mapping keys, and data without object-graph semantics. Aliases, explicit tags, and unknown or duplicate fields are explicitly rejected. Output uses a stable block form without a document marker.

## `std.xml`

XML uses the same mapper and shapes through Woodstox StAX streaming I/O. It shares public structural metadata with JSON; `@XmlAttribute` describes only XML attribute mapping. The format strictly handles root elements, attributes, fields, collections, maps, nullability, DTDs, and resource limits. It does not build a general XML tree.

## HTTP integration

The core `std.http` request body continues to use `Bytes` and does not depend on JSON. Generic `postJson` and `jsonBody` in the same package compose UTF-8 encoded bytes and consistently set `application/json`. Callers still explicitly bound response size and decode the response.

## Execution plan

| Stage | Status | Deliverable |
| --- | --- | --- |
| Extension functions | Complete | Unified syntax, binding, overloads, Core, formatting, and LSP support |
| Annotation policy separation | Complete | Decoupled passive targets and interceptor lifecycles |
| Structural reflection | Complete | Field metadata, ordinal reads, and runtime annotation queries |
| Serialization core | Complete | Mapper contract, recursive exact-type shapes, ordinal access, canonical constructors, and two-way plan cache |
| `std.json` | Complete | Jackson streaming, tree API, typed exceptions, and resource limits |
| `std.xml` | Complete | Woodstox streaming, attributes, structural round trips, typed exceptions, and resource limits |
| `std.yaml` | Complete | Jackson YAML streaming, shared token mapper, strict single-document handling, and typed exceptions |
| HTTP JSON | Complete | JSON request composition over the `Bytes` boundary and real loopback integration tests |
| Performance and closure | Complete | Exact-type cache gates, release-package validation, and architecture review |

Write parser, Core, runtime, or real integration tests before each stage's implementation. This table is the sole entry for execution status.

## Acceptance

- A `@Serializable value` recursively round-trips with deterministic rename, ignore, nullable, collection, enum, and Unicode behavior.
- Unsupported types, duplicate serialized names, unknown or missing fields, numeric overflow, and depth or size excess produce precise exceptions.
- Encoding field access and object construction use only ordinals. Decoding uses the precomputed name index, never invokes a method by string or uses JVM reflection or an intermediate JSON tree.
- The same exact type reuses its shape plan within a program; the cache never crosses an incorrect module or type identity.
- Loopback HTTP tests cover JSON request bytes, headers, and response decoding; public-network smoke tests live only in `norm/tests/live`.
- The development entry and formal distribution have the same public behavior.
