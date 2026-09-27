# Norm Package Manager

Module is the only dependency and publication unit. `Module module()` is the single write entry point for a module's identity and dependencies. A multifile module usually puts it in `module.norm`; a single-file application can put it beside business declarations and `Application application()` in the same `.norm` file, then run `norm <file.norm>` directly. Repository coordinates, publication metadata, and external implementations do not create new package kinds.

See the [module system](/spec/module-system) for local module graphs and visibility. [Java Library Adapter](/design/java-library-adapters) covers Java ecosystem integration, content identity, derived Maven metadata, and migration to pure Norm.

A namespaced Module `<namespace>.<artifact>` always publishes as `<namespace>:<artifact>:<version>`. A top-level Module uses its own name for both repository group and artifact: `orm@1` publishes as `orm:orm:1` and still produces `orm-1.nar`. `commons.lang@1` publishes as `commons:lang:1` and produces `lang-1.nar`. NAR means Norm Archive. `norm resolve` writes only content digests into `module.norm`; `norm package` produces a Maven repository layout, NAR, and POM. Gradle consumes the same Maven metadata.

NAR is the uniform Module artifact. Pure Norm and Java Binding modules use the same coordinates, dependency model, and archive format. A Binding is only an optional implementation; removing it does not change package kind or consumer calls.

External dependencies explicitly identify the Norm package source:

```norm
dependency(repository: "github", name: "micronaut.web")
```

`version` may be omitted. The repository then selects the latest stable integer version and immediately resolves it to exact coordinates. A published NAR contains only resolved, exact dependency versions. An explicit version directly selects `v<version>`.

`github` first reads the module-name mapping in [`normlanguage/registry`](https://github.com/normlanguage/registry), then downloads `<artifact>-<version>.nar` and its SHA-256 sidecar from the module's own GitHub Release `v<version>`. Releases are immutable. The registry determines only which repository owns a Module; it stores neither version lists nor artifacts. For a Java Binding in the NAR, the compiler resolves JARs, POMs, BOMs, and transitive runtime dependencies from Maven Central. Norm package sources and Java artifact repositories are distinct concepts; users do not write Maven or Gradle configuration.

Each Module has its own source repository. `module.norm` remains the single declaration source for identity, dependencies, and an optional Java root artifact. Third parties first claim a module name through a registry pull request, then can publish new versions independently from their own repositories. Adding a `norm` repository in the future will not change `github` resolution semantics.
