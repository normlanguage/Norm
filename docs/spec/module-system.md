# Module system

A module defines production and test source sets and its public boundary across packages. Module identity comes from a unique zero-argument `Module module()` declaration, not a file name. Directory projects usually put it at `<source-root>/<module-name-path>/module.norm` and the application entry in `application.norm` beside it. A single-file application may declare the module, business code, and application entry together in any `.norm` file:

```norm
Module module() {
  return module(
    dependencies: []
  )
}
```

At project startup, the toolchain first evaluates `Module module()` in isolation, then establishes the business source `ProjectSourceSet`. If there is a zero-argument `Application application()` but no explicit `Void main()`, the toolchain generates a hidden entry in the same package and calls `application().run()`; the returned object's static type must provide `Void run()`. A directory project uses `norm run <module-directory>`. A single-file application uses `norm <file.norm>` or equivalently `norm run <file.norm>`.

`Module`, `ModuleRequirement`, `module(...)`, `dependency(...)`, and `exportedDependency(...)` are defined by bootstrap source. The parameterized `module(...)` is an ordinary Norm factory returning a `Module` implementation; zero-argument `module()` is the user's entry. Module configuration may declare ordinary types and functions, implement its own `Module`, and import the standard library.

`name` is a dot-separated module name and the shared package prefix of a formal module. A local single-file application may omit `package`, `name`, and `version` together; the toolchain assigns an unpublishable internal identity for that build. When it declares a package but omits `name`, the name is derived from the package. For a directory module without `name`, the source package and directory mapping determine it; if derivation is not unique, it must be explicit. Module names `std`, `norm.bootstrap`, and internal identities beginning with two underscores are reserved for the toolchain prelude. `version` is a positive integer release version. Omitting it gives a local identity with version `0` that cannot be published. `exports` defaults to empty and names only public source made available to other Modules; the application and internal packages need not export their entries or implementations. A single-file application without a package cannot declare exports.

`dependencies` consist of repository, module name, and optional version. Without a version, the repository selects the latest stable release, then the project loader converts it into an exact dependency graph. A NAR stores exact versions only:

```norm
Module module() {
  return module(
    dependencies: [dependency(repository: "github", name: "base")]
  )
}
```

Local dependencies are at `<project-root>/dependencies/<module-name-path>/module.norm`, with dotted module names expanded into directories. The same coordinate is resolved only once from project dependency repositories. The toolchain recursively evaluates dependency configurations, verifies that returned coordinates exactly match their declarations, and rejects dependency cycles, selection of multiple versions of one module, and split packages owned by multiple modules.

## Source-file mapping

The project system joins the module name and export name, replaces dots with directory separators, and appends `.norm`. For module `std`, `collections.sequences` maps to:

```text
std/collections/sequences.norm
```

That file must declare `package std.collections`. Its file name does not determine the package name, but selects the concrete exported source file.

## Source set

When a module in `dependencies` is loaded, analyzed, or tested independently, the project root remains the workspace root containing that dependency directory. Local dependencies and virtual source from archived dependencies use the same root.

With a root module configuration, the source set contains business `.norm` source in the root module and its dependency graph, excluding all configuration files and undeclared nested modules. In a formal module, each business source file's relative directory must correspond exactly to its package and lie under the owning module name's package prefix. A local single-file application without a package uses the default namespace. A same-named file with a package declaration in a package directory is ordinary business source.

The Language Server overlays unsaved content before running the same project-loading lifecycle. Editors, the CLI, and test tools thus read the same module description and source set. If there is no adjacent `module.norm` but the current file declares `Module module()`, that file is the module root. Without a module declaration, the entry is treated as an independent single-file compilation unit.

`sources` and `tests` in module configuration declare production and test source directories, defaulting to `["."]` and `["tests"]` relative to the directory containing `module.norm`. Each directory uses the module name as a package prefix: in module `sample`, `src/math/value.norm` under `sources: ["src"]` declares `package sample.math`, while `tests/math/value_test.norm` under `tests: ["tests"]` may declare the same package.

```norm
Module module() {
  return module(name: "sample", version: 1, sources: ["src"], tests: ["tests"], exports: ["math.value"])
}
```

Different source directories may contribute to one package in a module but cannot duplicate a logical source path. The most specific configured directory determines a file's source set. Directories cannot escape the module root, and test source cannot be exported through `exports`. `std` and `std.test` are distinct packages that one `std` module may own; an external module cannot join it merely by declaring the same package name.

Ordinary compilation and publication load only production source. Tests and editor analysis load the root module's production and test source, while dependency modules load production source only. Even during test analysis, production declarations cannot depend on test declarations; `private` remains file-private. See the [testing API](/stdlib/testing-api) for test-function rules.

## Visibility

Source files in the same module and package load automatically and can refer directly to each other's `public` declarations. Cross-package use within one module requires an explicit import but not `exports`. A cross-module import can reach only `public` declarations in files selected by the target module's `exports`, and only when the target is a direct dependency or is explicitly exposed by a direct dependency through `exportedDependency(...)`. Ordinary transitive dependencies do not become visible. A composite Module can export dependencies to provide a stable platform boundary, leaving an application to declare only the composite Module. `private` is always limited to the declaring file. The standard library is an implicit dependency that the toolchain explicitly adds to every module's read boundary.

The standard library first evaluates its own `module.norm` through module bootstrap, then becomes the prelude shared by user module configurations and business programs. The project-lifecycle implementation entry is the `project` package in `cli/compiler`; the single bootstrap-protocol implementation is `cli/compiler/src/main/resources/bootstrap/module.norm`.
