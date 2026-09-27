# 12 Package and Module

Packages organize public names. Modules define source roots, export boundaries, and an exact dependency graph.

## Source files

An application entry point can be in a package:

<<< ../../norm/tests/docs/projects/packages/app/Main.norm{norm}

`package` must be the first declaration in a file and match its relative directory under the source root. `import` follows the package and imports only public names; `as` establishes a local alias for the current file. `private` always limits visibility to the declaring file.

A file without a package declaration is a standalone script. Scripts cannot import project sources, and project sources cannot import scripts.

## Module configuration

`module.norm` at the module root is an ordinary Norm source file that provides the single zero-argument module factory:

<<< ../../norm/tests/docs/projects/packages/app/module.norm{norm}

The module name and version participate in public nominal type identity. `exports` declares sources visible across packages. A cross-module import additionally requires the target to be a direct dependency of the current module; transitive dependencies do not automatically become visible.

The CLI, Language Server, and test tools read the same module description and source set. See the [module system](/spec/module-system) for complete path, dependency, and visibility rules.

Previous: [Annotation](/learn/annotations). Continue with the [Language Reference](/spec/language-spec), [Standard Library](/stdlib/overview), or [current status](/status) as needed.
