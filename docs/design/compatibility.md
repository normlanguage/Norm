# Compatibility policy

Norm evaluates source, binary, behavioral, and data-format compatibility separately. The ability to recompile a change does not imply that runtime behavior or persisted data remains compatible.

## Language versions

A source manifest declares its target language version. A compiler may read an older version, but must not silently interpret old code under new semantics. A formatter must not rewrite code into syntax unsupported by the target version.

## Examples of breaking changes

- Adding a keyword that invalidates existing identifiers.
- Changing operator precedence or evaluation order.
- Tightening generics or nullable rules.
- Adding a variant to a public enum.
- Removing or renaming a public function parameter, since named arguments are part of the call contract.
- Changing default serialized fields or the interpretation of time or Decimal values.

## Standard library

A stable minor version may add APIs that do not introduce overload ambiguity and fix implementations that contradict the specification. Removing an API, changing a return type, or adding a required parameter needs a major version. If a security fix must change behavior, its announcement should explain the risk and migration path.

## Preview period

Before the first stable release, full compatibility is not promised. Every preview must still publish its differences and migration guidance. Frequent changes do not justify omitting records.

Compatibility tests should retain old source, compiled artifacts, and representative serialized data and run them against release candidates.
