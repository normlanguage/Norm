# Package Syntax

```text
PackageDeclaration := "package" Identifier ("." Identifier)* ";"?
```

A package declaration is the first declaration in a project source file. Its name uses dots; the ending semicolon is optional.

```norm
package geometry.shapes

class Circle {
    Integer radius
}
```

A file declares at most one package. The package name must match the relative directory beneath the source root. Several files may declare the same package; their public names form the package API together.

A file without a package declaration is a single-file script. Scripts cannot import project sources, nor can project sources import scripts.

Duplicate public names, collisions between package names and import aliases, and a mismatch between path and package are compile errors.
