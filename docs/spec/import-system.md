# Import system

`import` brings public names from other packages into the current file. Imports affect name resolution only; they do not execute code or change visibility.

```norm
package drawing

import geometry.Point
import geometry.area

Point origin = Point(x: 0, y: 0)
```

## Rules

- Imports must follow the package declaration and precede all other declarations.
- An import names one specific declaration by default.
- If two imports yield the same short name, the file must use a qualified name or an explicit alias.
- Unused imports produce warnings without changing program semantics.
- Import paths are case-sensitive and match the declaration's fully qualified name.
- A declaration imported across packages must be `public`, and its file must be exported by its module.

```norm
import geometry.Point as GeometryPoint

GeometryPoint point = GeometryPoint(x: 2, y: 3)
```

Wildcard imports are not part of the core syntax for now: adding a public declaration should not silently change name resolution in existing files. The standard-library prelude must remain small and be fixed by the language version.

Module declarations and imports serve separate purposes: `module.norm` determines which source files may be used across packages; an import selects which of their declarations to introduce into the current file. Version selection does not appear in imports.

The applied example combines public imports from a packaged library with Norm enums, values, and exhaustive matching. [Prepare the Commons Lang package](/learn/commons-lang) before running it:

<<< ../../norm/tests/docs/libraries/commons-lang/prepare_headings.norm{norm}

<<< ../../norm/tests/docs/libraries/commons-lang/prepare_headings.out{text}
