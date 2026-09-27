# Import Syntax

```text
Import := "import" QualifiedName ("as" Identifier)?
```

Imports follow the package declaration and precede other declarations. They import public names only.

```norm
package drawing

import geometry.Point
import geometry.render as renderPoint
```

## Name resolution

Without an alias, the last name segment becomes the short name in that file. A local declaration takes precedence over an imported name, but the compiler should warn about the shadowing. Two imports producing the same short name are an error unless at least one uses an alias.

Imports are not transitive: if package A imports B, users of A do not automatically see B. Importing also runs no initialization code.

The current core grammar has no wildcard import. Standard preimports contain only basic types and a few core functions, fixed by language version. See the [import system](/spec/import-system) for the complete package boundary.
