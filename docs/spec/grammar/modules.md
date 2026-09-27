# Module Configuration

Module configuration is ordinary Norm source and shares imports, declarations, expressions, and function-body syntax with business code. A directory project usually provides configuration in `module.norm` in its root package directory. A single-file application can provide it directly in the business file:

```norm
Module module()
```

A local single-file application may omit its `package`, Module name, and version. That internal application identity cannot be published or declare exports. A formal module still uses package structure as its public namespace.

A common implementation calls the parameterized factory in bootstrap sources:

```norm
import std.math.max

String projectName() {
  return "sample"
}

Module module() {
  return module(
    name: projectName(),
    version: max(left: 1, right: 1),
    exports: ["Main", "model.User"]
  )
}
```

Dependencies are represented by `List<ModuleRequirement>` and constructed with `dependency(String repository, String name, Integer? version = null)`. `repository` is part of dependency identity and cannot be omitted. When `version` is omitted, the repository resolves the latest stable version. See the [module system](/spec/module-system) for complete semantics.
