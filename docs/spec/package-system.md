# Package system

A package is a namespace for Norm source code. One source root may contain multiple packages, and multiple source files may contribute to one package; declaration order and file names do not participate in name resolution.

```norm
package geometry

class Point {
    Integer x
    Integer y
}

Integer area(Integer width, Integer height) {
    return width * height
}
```

## Files and source roots

Package names consist of dot-separated identifiers. Each root of a module source set uses the module name as its package prefix; the relative directory beneath that root completes the package name. See [source sets](/spec/module-system#source-set) for configuration and directory mapping.

A file name does not create a namespace or limit its number of public declarations. A project may name files after their principal type, but this is only an organizational convention. Cross-file name resolution occurs only within the source set established by `module.norm`. Without module configuration, the entry is treated as an independent single file whether or not it declares a package.

## Visibility

- Top-level declarations are `public` by default and can be used directly by files in the same package. They may also be imported from other packages when their source file is exported by the module.
- A `private` top-level declaration is visible only within its source file.
- Norm provides neither package-private nor `protected` visibility.
- A public signature cannot expose a private type.

## Compilation boundary

The compiler first collects all declaration signatures from loaded project source, then resolves imports and function bodies. Different files may therefore refer to each other and may form function-recursion or declaration-only dependency cycles. Source has no mutable top-level initialization, so name resolution does not depend on file order.

Packages organize source namespaces; `module.norm` defines source sets and module boundaries. Files in the same package and module may refer to each other directly. `exports` precisely determines visibility across packages. See the [module system](/spec/module-system).
