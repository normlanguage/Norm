# Norm Runtime Metadata Model

Norm keeps runtime type information because generics are reified.

`List<String>.class` contains:
- generic arguments
- methods
- fields
- runtime type information

Metadata also stores:
- dynamic type
- interface dispatch data
- GC information
- reflection information

Reflection is explicit and controlled.
