---
title: Java Library Adapter
description: Goals, boundaries, and delivery model for using a Java JAR as an ordinary Norm Module implementation
---

# Java Library Adapter

Java application declaration projection is defined by [JavaStubPlanner](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaStubPlanner.java). [PlainObjectJavaBridgeTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PlainObjectJavaBridgeTest.java) verifies typed collection fields and methods across the Java boundary.

## Goal

A Norm Module can implement its public Norm API using a Java JAR and its runtime dependencies, then replace that implementation with pure Norm in a later version. Module names, exports, dependency declarations, and publication coordinates do not expose implementation provenance.

```text
Norm API → optional JAR binding → Java dependency graph
Norm API → Norm Core
```

Both paths produce the same kind of Module artifact. Consumers depend only on the Module.

## File identity

Every `.norm` file is Norm source. A top-level `Module module()` supplies the module declaration. A separate `module.norm` is the conventional multi-file layout; single-file applications can place it beside business declarations. Module declarations and auxiliary entry points use the same parsing, type checking, Core lowering, and execution pipeline. A JAR declaration is simply an ordinary Norm object returned by that function. Generated adapters also use only public Norm syntax, types, and functions. The compiler carries a set of generated origins that source cannot forge, and exposes frozen Binding intrinsics only to those documents. Content distinctions define language and module semantics, not host authorization.

## Module boundaries

`Module module()` is the single declaration point for module identity, dependencies, and publication configuration. A working directory is not a dependency or publication unit, and there is no Project manifest.

A Module has at most one optional `jarBinding`, containing one root JAR or JDK module. `jarBinding.api` can select public Java types from that root dependency graph. Types already exposed by a dependency Norm Module reuse that declaration. Supporting metadata is defined by [JavaApiScanInput](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaApiScanInput.java).

`jarType(name: "jakarta.persistence.EntityManager", alias: "internal.Store", members: [])` explicitly names the module-relative declaration path. Without `alias`, the Java short class name is used, joining enclosing names for nested classes. `exports` selects public source files and never names bindings; omitted or empty exports keep bindings internal. The normative entries are [BindingPlanner](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/BindingPlanner.java) for naming and the [module system](../spec/module-system.md) for visibility.

Ordinary Norm source, generated declarations, and pure Norm implementations share one export table. A module may place Java adapters in an `internal` package and export only its authored Norm API.

## Declaration model

Java annotation adaptation projects the field-initialization responsibility of `jakarta.inject.Inject` onto the standard-library `ManagedField` contract. The frontend determines constructor parameters from resolved annotation contracts, not third-party short names. The DI container still injects existing objects.

Java annotations directly marked with `io.micronaut.aop.Introduction` project onto `ManagedImplementation`, retaining their original targets and retention. The contract identifies an external implementation provider. Recognizing it and linking method implementations are distinct stages; unlinked methods must not become empty implementations. See [JavaAnnotationContract](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationContract.java).

```norm
Module module() {
  return module(
    name: "commons.lang",
    version: 1,
    exports: ["StringUtils"],
    dependencies: [],
    binding: jarBinding(
      target: mavenJar(
        group: "org.apache.commons",
        artifact: "commons-lang3",
        version: "3.20.0",
        resolution: sha256("...")
      ),
      api: [
        jarType(
          name: "StringUtils",
          members: ["isBlank", "isNotBlank", "reverse", "split", "trim"]
        )
      ]
    )
  )
}
```

Declarations and defaults are defined only in [bootstrap/module.norm](../../cli/compiler/src/main/resources/bootstrap/module.norm). Source and NAR acceptance for internal binding isolation is indexed in [ModulePackagerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ModulePackagerTest.java).

Local JARs use `localJar(path, integrity)`. `norm resolve` resolves dependencies and atomically fills missing digests. A declared digest mismatch fails immediately; authors updating dependencies must first change their declarations. `norm run`, `norm package`, and CI verify declared content without accepting dependency drift. No separate lock file is used.

The provided [`java.base` module](../../norm/stdlib/java/base/module.norm) owns JDK `java.base` reference types. The standard library and library bindings reuse those declarations through ordinary module reads and Java calls. Minimal scalar projections are defined by [JavaPlatformTypes](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaPlatformTypes.java); other Java types retain their nominal identity. Binding modules cannot declare another owner for these JDK types. Calendar and decimal execution coverage is indexed in [JavaBaseValueIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaBaseValueIntegrationTest.java).

JDK roots use `jdkModule(name, resolution)`. [JdkModuleArchive](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JdkModuleArchive.java) defines the pinned metadata snapshot; it is excluded from the runtime classpath. [JavaApiScanInput](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaApiScanInput.java) carries the root and supporting metadata explicitly. Published binding compatibility is defined by [PublishedJarBinding](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/PublishedJarBinding.java).

## Initial usage

Scalar interface relationships derive from JDK signatures through [JavaScalarConformances](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaScalarConformances.java) and the existing built-in conformance pipeline. Execution coverage is in [JavaScalarInterfaceIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaScalarInterfaceIntegrationTest.java).

Place local JARs inside the Module directory, such as `lib/tools.jar`. A `jarType` name identifies one unique public class in the resolved Java dependency graph. `members` selects constructor, method, or field names and includes stable public overloads of each name; constructors use `new`. Java reference types appearing in signatures form the minimal declaration closure. The compiler generates ordinary Norm declarations for selected APIs: for example, `StringUtils.reverse` becomes `stringUtilsReverse`.

```norm
Module module() {
  return module(
    name: "example.tools",
    version: 1,
    binding: jarBinding(
      target: localJar(path: "lib/tools.jar"),
      api: [jarType(name: "Tools", members: ["new", "convert"])]
    )
  )
}
```

Initial resolution writes the digest back into the declaration:

```text
norm resolve path/to/example/tools
norm run path/to/example/tools/Main.norm
```

Maven root artifacts use the declaration model above. Another Module declares an ordinary Norm dependency and imports generated functions:

```norm
import commons.lang.stringUtilsReverse

Void main() {
  printLine(stringUtilsReverse("Norm") ?? "")
}
```

Before publication, run `norm resolve`, then generate artifacts in a directory that can serve directly as a Maven repository:

```text
norm package path/to/commons/lang --output path/to/repository
```

Repository coordinates and artifact names derive from Module identity; see the [package manager](/ecosystem/package-manager). Maven and Gradle can consume the generated NAR and POM. Another Norm project's `dependency(repository, name, version?)` resolves those same coordinates without a POM, Gradle file, or lock file.

See the [Apache Commons Lang example](https://github.com/normlanguage/commons-lang/blob/main/samples/README.md) for a runnable directory.

## Host resource ownership

`jarType(..., borrowed: ["child"])` declares receiver-owned reference getters. Constructors and unmarked resource returns establish an execution-domain owner only for a new host identity; aliases retain its existing owner. Borrowed views retain their receiver and cannot close or transfer the resource. Use `ownResourceInContext(value)` from `std.io` to register an owned Java resource with the current `ResourceOwner`. This composition preserves the Java value and its canonical lifetime; Java bindings do not depend on the standard-library owner protocol. Explicit closure releases its registration and preserves the first close failure across aliases.

The declaration lives in [module.norm](../../cli/compiler/src/main/resources/bootstrap/module.norm); the standard owner API lives in [ownership.norm](../../norm/stdlib/std/io/ownership.norm). [HostResourceOwnershipTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/HostResourceOwnershipTest.java) exercises source and packaged adapters. [PublishedOwnershipContractTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PublishedOwnershipContractTest.java) verifies the published ownership contract; [PublishedJarBinding](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/PublishedJarBinding.java) defines the supported archive ABI.

## Content identity

Paths and Maven coordinates locate content. The implementation derives these identities:

- `JarContentId`: complete JAR bytes.
- `JavaApiId`: normalized bindable public API.
- `ResolvedJarGraphId`: artifact content and dependency edges.
- `BindingArtifactId`: dependency graph, mapping policy, and Binding ABI.
- `ModuleApiId`: public Norm declarations.
- `ModuleImplementationId`: Norm Core and optional Binding implementation.

Identical content shares scanning and Binding caches. A changed JAR implementation requires relinking; consumer source remains valid when the public Norm API is unchanged.

## Publication model

`norm package` produces a NAR and a POM derived from `module.norm`. [ModuleArchiveFormat](../../cli/compiler/src/main/java/dev/w0fv1/norm/value/ModuleArchiveFormat.java) owns the archive version. Every Module stores its evaluated manifest, complete production sources, and resources; `exports` defines public APIs rather than selecting artifact files. Java Binding Modules also retain an API report and the stable binding artifact defined by [PublishedJarBinding](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/PublishedJarBinding.java). Consumers verify binding ABI, artifact digests, module descriptors, pinned dependency graphs, public type ownership, and archived sources, then directly link published artifacts. Application-specific callbacks, annotation processing, and reachability pruning remain application-build responsibilities. Java graph artifacts are retained by [ModulePackager](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ModulePackager.java); consumers do not execute remote `module.norm` source. Pure Norm and Java adapters share one package model. See [ModulePackagerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ModulePackagerTest.java) and [CrossModuleJarBindingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/CrossModuleJarBindingTest.java) for archive and cross-module acceptance.

The binding ABI covers data structures, serialization, and runtime conventions. Changes to those contracts require updating `PublishedJarBinding.ABI` and republishing adapters; unrelated compiler implementation changes do not.

All published modules also carry the Core artifact defined by [CompiledModule](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompiledModule.java). The manifest validates its digest and ABI. The [compiler architecture](/spec/compiler-design) defines import rules and compilation-work acceptance. Changes to the payload's data structures or serialization require updating `CompiledModule.ABI`; Core and language-semantics versions follow their existing identity contracts.

POMs declare root Java artifacts and ordinary Maven dependencies. Resolving a Norm Module also obtains the required Java graph. Publishing a local JAR requires resolvable publication coordinates; the same publication produces a Java artifact and a dependent Norm artifact.

A pure-Norm implementation never replaces an existing Binding artifact in place. Implementation migration publishes a new version of the same Module.

## Mandatory constraints

- Modules have no Java-specific kind.
- Each Module binds at most one Java root: a JAR or a JDK module.
- Do not generate public callable APIs for transitive dependencies.
- Do not expose arbitrary host-class lookup, reflective calls, or untyped host objects.
- Java objects appear in Norm as opaque references with definite declaration identity.
- Explicit bindings of different versions of the same Java artifact across Modules must fail. The shared classpath resolver selects transitive versions, preferring explicit roots and otherwise using Maven version ordering; only selected versions' dependency closures remain.
- Different content at fixed Java coordinates, or an API fingerprint mismatch, must fail.
- Unsupported signatures on the selected public surface produce deterministic diagnostics.
- Remote artifacts carry compiled module descriptors; consumers do not execute publisher configuration source.
- Maven POMs, digest manifests, and generated declarations are derived artifacts. Gradle consumes the same Maven metadata directly.

## Current binding surface

`JavaModulePath` owns Java module identification and selection of root-JAR JPMS dependencies. JVM execution, prepared artifacts, and Native share module-root identity. JVM module resources and class loading use the same application execution domain; the application loader closes resource streams on exit. Entry points are `JvmJarBindingRuntime`, `PreparedApplication`, and `NativeApplicationExecutable`. `JavaModuleLoadingTest` verifies module selection, identity, resource access, and file release. [ApplicationClassLoaderIsolationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ApplicationClassLoaderIsolationTest.java) verifies isolation of compiler dependencies, service resources, and the JDK platform boundary. Native compatibility still requires actual application acceptance.

Current support includes static and instance methods, constructors, static and instance fields, primitive and boxed scalars, strings, `Number`, opaque objects, Object-bounded generics, and generic inheritance projections within a JAR. Java arrays with concrete component types become generated identity wrappers supporting fixed length, reads, in-place updates, and construction. Primitive and boxed arrays retain distinct nominal types rather than mapping to value-semantic Norm `Array<T>`. Java `T[]` and `T...` use reified arrays distinguished by erased component type; a varargs call takes one array argument.

Java objects retain nominal type and host identity. The execution layer handles thrown exceptions and resource lifetimes; see [JavaValueAdapter](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/JavaValueAdapter.java) and [ResourceScope](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/ResourceScope.java). Runtime subtype coverage is in [JarReferenceProjectionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JarReferenceProjectionTest.java).

Public interfaces in the root JAR become ordinary Norm interfaces, retaining projectable generic inheritance. Generated concrete classes implement their corresponding interfaces. Interface methods are ordinary Norm methods; private binding carriers preserve JVM identity for objects returned through interfaces. The shared type relation drives this mapping, while user source uses only Norm interfaces, classes, and method calls.

Public interfaces inherited through package-private Java parents are restored in generated declarations, substituting generic arguments along the full hierarchy. Java unbounded wildcards project to the Norm existential type `?`, allowing `Iterable<String>` to pass safely to `Iterable<?>` parameters.

Member selection considers the complete public inherited surface, substituting parent type variables in the exported class. Calls retain publicly linkable declaration owners; package-private declarations are linked through the exported class. The census records real declarations without duplicating inherited views. Public dependency-JAR types participate in inheritance and SAM identification and may be selected by the configured API.

Java `Class<T>` maps to Norm `Class<T>?`. The generator derives JVM descriptors for public wrapper declarations and array wrappers. Runtime resolution uses declaration identity to map real `java.lang.Class` values in both directions; when a return value has multiple valid erased views, the call site's `Class<T>` disambiguates them. Ordinary Norm types without Binding mappings cannot be resolved through string class names or host reflection.

Class-token identity projection is defined by [JavaTypeProjector](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaTypeProjector.java). Java generic bounds remain ordinary Norm generic constraints.

Root-JAR enums become closed Norm `enum` declarations whose public constants are payload-free variants. Java static methods become ordinary functions; instance methods become functions with the enum value as their first argument. Parameters, results, and enum-array elements convert through declaration and constant identities in both directions. For Java identifiers outside Norm's identifier set, the generator preserves stable reversible variant mappings.

Java domain values, shared collection identity, generic elements, Optional empty states and stream resources are exercised by [JarBindingRuntimeIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JarBindingRuntimeIntegrationTest.java). Type ownership is covered by [JavaBaseDomainBindingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JavaBaseDomainBindingTest.java).

Java standard functional interfaces and public root-JAR SAM interfaces map to native Norm `Function<R(P...)>`. Projection resolves standard `? super` inputs and `? extends` outputs; root-JAR SAM generic arguments are substituted at their use sites. Runtime adaptation creates real Java interface instances from Norm lambdas, captured closures, and function references. Host callbacks enter Norm on their calling thread, preserving thread-local contexts such as transactions during nested host calls. Norm releases exclusive execution ownership while host code runs and reacquires it on return. Nested host calls blocking for callbacks from another thread follow the same boundary. Synchronous, asynchronous, and internally awaited Java callbacks share argument, result, and exception propagation rules. See [GuestCallbackScheduler](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/truffle/GuestCallbackScheduler.java).

Each package's `binding/java-api.json` is the complete machine-readable declaration/adaptation census. `jar.api` in `module.json` is the machine-readable contract for the published public surface. Publication requires generating that entire selected surface and passing behavioral tests.

Java annotations become ordinary typed Norm annotations. At JVM application boundaries, annotations on Norm applications become real Java annotations. Modules needing compile-time processing declare official JSR 269 processors as ordinary dependencies; application builds generate isolated Java inputs and run the processors automatically. Generated application types retain Norm generic inheritance and provide managed instance allocation through JVM application facades. Framework-created entities or components associate with the same Norm objects. Processing includes the entry Module and pure Norm dependencies containing framework-support source, but excludes generated Binding declarations. Norm exceptions and enums retain their language semantics across Java proxies such as DI and transaction boundaries. See the [Micronaut BBS](https://github.com/normlanguage/examples/blob/d287c8b9223d20f20fe9f6464dc0c7c0de0fa4a8/micronaut-bbs/README.md) for real-framework acceptance.

Module `resources` can supply annotation-processor compilation inputs. [ApplicationCompiler](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java) prepares resources; [AnnotationProcessorResourcesTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/AnnotationProcessorResourcesTest.java) verifies template updates and deletion.

When every implicit-construction input has a default, the Java application facade offers a preferred no-argument constructor that runs Norm initialization. The full-argument entry remains available. [JavaAnnotationBindingIntegrationTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java) verifies construction and private state across languages.

Core conformance witnesses connect interface default implementations to Java default methods, letting multiple implementing classes share one entry point. Method bodies still execute through the Norm runtime. Standard-interface parents without a Java representation do not generate `extends Object`. The same cross-language tests verify default-method inheritance and receiver dispatch.

Java application facades map zero-, one-, and two-argument functions to JDK functional interfaces according to arity and Void returns. Generic arguments recursively follow the same type projection. [JavaFunctionShape](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaFunctionShape.java) unifies declarations and runtime adaptation. Cross-language tests cover bidirectional calls, function identity, boxing, and exception propagation. Java-supplied functions have no Norm source-declaration metadata.

Java method indexes retain declaration identity separately from execution implementation, allowing distinct methods to share normalized bodies. Cross-language calls verify shared default lifecycles.

Managed class method signatures project to Java abstract methods, with class abstractness determined by inherited dispatch targets. Real javac and reflection validate method/parameter annotations, generic return types, and parameter names. See [JavaManagedMethodProjectionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/JavaManagedMethodProjectionTest.java). Abstract declarations enter the host method index, not the local execution-entry set.

Norm-originated host calls retain concrete receiver and method type arguments; inherited views reuse CoreTypeRelations. When Java facades call ordinary methods on generic parents, the associated Norm object supplies the receiver type. [JavaApplicationDispatch](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/truffle/JavaApplicationDispatch.java) coordinates application object identity, field synchronization, and callbacks. Direct Java calls to Norm methods with method type parameters, and direct construction of uninstantiated generic classes, remain unsupported.

Java collection signatures use nominal `java.base` types. [JavaAnnotationBindingIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java) verifies nested Java lists, nullable elements, and object identity across application callbacks. Norm annotation eligibility for Java emission is defined by [JavaAnnotationShape](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationShape.java); native metadata remains available independently of Java emission.

[JavaHostSurface](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaHostSurface.java) defines application exposure. [JavaApplicationLinkage](../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/JavaApplicationLinkage.java) carries generated types and calls; [JavaValueAdapter](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/JavaValueAdapter.java) centralizes scalar conversion.

Nullability on fields, parameters, results, and generic arguments projects through standard JSpecify `Nullable` type annotations. Java annotation-processing environments explicitly include JSpecify, so frameworks need not infer nullability from boxed types. [JavaManagedMethodProjectionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/frontend/JavaManagedMethodProjectionTest.java) verifies reflection on nested collections and nullable type variables.

## Acceptance

- Module source trees contain no `lock.norm`, handwritten POM, or Gradle configuration.
- The same `module(...)` factory describes pure Norm and JAR-backed Modules.
- The type structure cannot declare two Java roots for one Module.
- Maven JARs, local JARs, and JDK modules use the same Binding pipeline.
- Replacing a JAR at the same path triggers a digest mismatch.
- Identical JAR content can reuse a Binding artifact across paths.
- A pinned Commons Lang version can be resolved from a Maven repository and called from Norm.
- A packaged Module works as an ordinary dependency in another project and resolves its Java dependencies.
- Removing Binding and providing source with the same Norm exports preserves consumer imports and call syntax.

Application-level Java resources register [JavaApplicationResource](../../cli/compiler/src/main/java/dev/w0fv1/norm/bridge/JavaApplicationResource.java) through standard ServiceLoader. [JvmJarBindingRuntime](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JvmJarBindingRuntime.java) owns their lifecycle and closes them before releasing the application class loader. Closing a child resource such as a window does not terminate the application runtime.

[JarResourceScope](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarResourceScope.java) retains cached JAR resource URL handles until the last runtime releases them, including URLs reconstructed from strings. [JvmJarBindingRuntimeTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JvmJarBindingRuntimeTest.java) verifies stream closure, shared runtimes, and file release.

JPA `jakarta.persistence.Id` and `jakarta.persistence.EmbeddedId` map to `std.annotation.IdentityField` while retaining their Java annotation identities. [JavaAnnotationContract](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationContract.java) owns the mapping; see [declaration references](../spec/declaration-references.md) for reflection queries and field identity.

Generated declarations preserve public Java superclass relationships, substituting generic arguments across package-private intermediates. [CrossModuleJarBindingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/CrossModuleJarBindingTest.java) verifies cross-module source and published artifacts. The standard-library ABI supplies binding-construction tokens centrally.

Projecting the same Norm function to the same SAM type preserves host-object identity. Weak-reference caches are isolated by application execution domain. [JarBindingConcurrencyIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JarBindingConcurrencyIntegrationTest.java) verifies identity across calls and callback execution.

Published Java dependency packaging and verification: [BundledJarGraphs](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/BundledJarGraphs.java), [ModulePackagerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ModulePackagerTest.java).
