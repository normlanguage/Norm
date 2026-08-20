# Norm Object Layout

Runtime objects contain:

- type metadata pointer
- GC metadata
- fields
- interface dispatch information

Value objects may use optimized inline representation.

Ref objects use managed identity storage.
