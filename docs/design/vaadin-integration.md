---
title: Vaadin integration plan
description: Boundaries and acceptance for declarative reactive Norm pages, standalone Jetty, and Spring integration
---

# Vaadin integration plan

This document defines adaptation boundaries, implementation stages, and acceptance criteria. Adapter module sources own public signatures. The adapter workspace README (`norm-vaadin/README.md`) defines supported execution modes.

## Goals and scope

Store page state in ordinary Norm fields, express derived content with ordinary functions and closures, and compose real Vaadin components with result builders. Provide separate entry points for standalone Jetty and official Spring Boot integration. Both share component APIs, reactive bindings, and frontend resource builds.

- Implement Vaadin, Jetty, and Spring adapters outside the compiler repository and deliver them as ordinary Norm Modules.
- Do not depend on, copy, or modify the JavaFX `ui` package. Desktop examples are references for expression style only.
- Add no Vaadin-specific `std` types, builtins, keywords, or special class-name recognition.
- `FieldHandle` is an existing builtin. Do not move it into `std` or redefine that name. Targeted compilation tests determine whether two-way binding can directly use its existing parameter-context conversion.
- Reuse field observation, contexts, tasks, and resource protocols. Justify the responsibility and necessity of any general gap separately.
- Do not modify `micronaut.web` or implement Micronaut integration.
- Introduce no general DI framework, virtual DOM, or JavaFX component abstraction.
- Do not commit, publish packages, or deploy without explicit permission.

## Facts and entry points

| Capability | Authoritative entry point |
| --- | --- |
| Content blocks, ordinary `if`, and loops | [Result builders](../spec/grammar/result-builders.md) |
| Trailing closures and block call chains | [Advanced function rules](../spec/grammar/functions-advanced.md) |
| Property access and object fields | [Class syntax](../spec/grammar/classes.md) |
| Observing field reads and writes | [fields.norm](../../norm/stdlib/std/observation/fields.norm), [FieldObservations](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/FieldObservations.java) |
| Field-context capture and write-back | [FieldHandleExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldHandleExecutionTest.java) |
| Field assignment and notification | [FieldWriteNode](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/FieldWriteNode.java) |
| Task scheduling and cancellation | [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm) |
| Resource ownership | [ownership.norm](../../norm/stdlib/std/io/ownership.norm) |
| Modules and Java artifacts | [Java Library Adapter](java-library-adapters.md), [package manager](../ecosystem/package-manager.md) |
| JVM and Native delivery | [Application builds](../tooling/application-build.md) |
| Local module and resource snapshots in application archives | [ApplicationBundleWriterTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/build/ApplicationBundleWriterTest.java) |
| JAR filenames and packaged module resolution | [ResolvedJarClasspathTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ResolvedJarClasspathTest.java), [BundledJarGraphsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/BundledJarGraphsTest.java) |
| JVM resource connections and stream lifecycles | [JvmJarBindingRuntimeTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JvmJarBindingRuntimeTest.java) |
| Application/compiler dependency isolation | [ApplicationClassLoaderIsolationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ApplicationClassLoaderIsolationTest.java) |

Existing observation support does not establish complete reactive Vaadin integration. Verify that Norm callbacks crossing Java boundaries retain the correct dependency-tracking and UI execution contexts.

### Cross-package Java type prerequisites

Cross-package signatures preferentially reference public Java types supplied by declared dependencies. External types without a public owner remain module-internal opaque declarations. Typed transfer between page components and hosts must use this general mechanism, not `Any` or Vaadin-specific conversions.

[ProjectJarBindingLinker](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ProjectJarBindingLinker.java) resolves type ownership. NARs retain derived mappings for public exports for analysis. Runtime linking revalidates those mappings and generated sources against pinned JARs and module dependencies. Do not merge arbitrary Norm types by Java class name.

See [BindingPlanner](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/BindingPlanner.java) for generation and [JarBindingImportsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JarBindingImportsTest.java) for declaration reuse and renamed exports. Generator tests do not replace module-resolution and archive round-trip acceptance.

[CrossModuleJarBindingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/CrossModuleJarBindingTest.java) covers local and archived execution, generics, renamed exports, direct and re-exported dependencies, diamond dependencies, ambiguous type-owner diagnostics, and generated-source validation.

## Package boundaries

These are logical module boundaries. Each module still binds at most one Java root JAR. Aggregate modules compose dependencies without generating duplicate public types.

| Module | Responsibility | Lifecycle owner |
| --- | --- | --- |
| `vaadin` and component bindings | Components, content builders, ordinary-field bindings, page contract | Page or component scope |
| `jetty.server` | Standalone Servlet container, installation, listening, sessions, WebSocket, shutdown | Standalone server object |
| `vaadin.jetty` | Vaadin installation, page factory, standalone application entry | Delegates to `jetty.server` |
| `vaadin.spring` | Official Spring integration, page creation, beans, and scopes | Spring application context |
| `spring.boot` | Boot startup/shutdown, configuration, application entry | Spring application context |

`vaadin.spring` does not depend on the standalone Jetty launcher. When Spring selects Jetty, Boot's official mechanism manages the server. Shared underlying artifacts do not imply shared startup responsibilities.

Paths beginning with `norm-vaadin/...` refer to a separate adapter development workspace, not files in this repository or documentation-site pages. Each published module has its own `module.norm`; publication repositories follow module ownership rules. Java build configuration maintains bridge, host, and application frontend artifacts, which Norm applications consume through pinned module dependencies.

## Page-author contract

The shared page example (`norm-vaadin/norm/dependencies/example/profile/profile.norm`) defines executable syntax; the component entry point (`norm-vaadin/norm/dependencies/vaadin/components.norm`) defines public signatures. Two-way input for ordinary fields uses existing typed field capture. An ordinary string value is not a writable address. Page authors do not need `State<T>`, `.value`, or `computed`.

- Pass fixed properties as values and dynamic properties as closures.
- Ordinary functions track actual reads when executed inside bindings; outside bindings they remain ordinary functions.
- Dependency identity includes object identity and field identity, without changing String or Boolean value semantics.
- Create page objects independently for each UI; user state is not shared by default.
- Ordinary `if` and `for` inside builder blocks retain their evaluation semantics.
- Dynamic structure uses explicit regions and lists maintained by business keys; property changes do not rebuild the entire page.
- Define input synchronization timing and programmatic write-back without duplicating business submission events.
- Spring and standalone hosts share the page contract; dependency provision belongs to the integration boundary.

## Reactive implementation

Prefer connecting existing field observation to Vaadin Signals, reusing official component-binding dependency tracking and lifecycles rather than building another general reactive engine in the adapter.

Observe page objects before executing their bindings. During a binding, field reads enter the appropriate dependency nodes; writes invalidate those nodes. Rebuild subscriptions from actual reads on each computation. Explicitly define observation of nested business objects: observing a root object does not establish coverage of arbitrary object graphs.

One UI event can modify multiple ordinary fields. Binding refresh should see the state after that event completes. Batching defines notification boundaries; it does not promise transactional rollback of ordinary Norm field assignments.

Derived computations must be read-only. Automatic rejection of writes inside bindings requires detection before mutation. Mutating a field and then throwing does not leave state unchanged. Current field notifications occur after writes, so validate a feasible implementation separately rather than treating a post-write workaround as complete.

Test field replacement, internal changes to value collections, and internal changes to identity objects separately. Closed component scopes release field subscriptions and Java callbacks. Dynamic branch replacement and list removal must release old scopes. Keyed lists require tests for reordering, same-key data changes, duplicate-key errors, and preservation of editing state.

## Execution and resource boundaries

- Startup succeeds only after the port is bound and Vaadin is initialized.
- Each application has one startup/shutdown owner.
- Waiting for the server must not hold a Norm execution lock that blocks event callbacks.
- Background work connects existing TaskScope and TaskExecutor capabilities to UI access; it does not introduce another asynchronous language.
- Closing a page cancels tasks or invalidates their results; completion callbacks cannot update a closed UI.
- Shutdown stops accepting work, closes pages and sessions, stops the container, then releases the Norm execution domain.
- Startup failures clean up through the same ownership boundaries; initialization and shutdown errors are not swallowed.
- Real concurrency tests must cover framework threads, Norm callbacks, and UI session locks.

The Spring application context is the single source of shutdown state. Waiting must also handle closure through Spring's own entry points and finish only after resources are destroyed. See BootApplicationTest (`norm-vaadin/spring-boot/src/test/java/dev/normlanguage/spring/BootApplicationTest.java`).

## Frontend and builds

Both hosts reuse Vaadin's official frontend build. A precompiled production bundle is suitable only when using official standard components and meeting official conditions. Custom themes, resources, or components require a real build and must not be silently omitted.

The application resource module owns the production bundle. General component bridges and host packages carry no application build configuration. The shared example builds through profile-frontend (`norm-vaadin/profile-frontend/build.gradle.kts`), whose artifact reaches both hosts through ordinary Norm module dependencies.

Application themes use Vaadin 25's `@StyleSheet` entry point. The application resource module registers `AppShellConfigurator` through a standard Java service declaration. Spring autoconfiguration uses this to register the application package, while Vaadin still instantiates and initializes the shell. See ProfileShell (`norm-vaadin/profile-frontend/src/main/java/dev/normlanguage/example/profile/ProfileShell.java`) and its service declaration (`norm-vaadin/profile-frontend/src/main/resources/META-INF/services/com.vaadin.flow.component.page.AppShellConfigurator`).

Norm Core method bodies are not ordinary Java method bodies, so Java bytecode scanning may miss actual component use. Derive component usage from the authoritative compilation/adapter inputs rather than requiring authors to maintain a manual component allowlist. Artifacts must contain startup configuration and all browser resources.

The application frontend uses the official full scanning mode to collect component annotations, avoiding omissions caused by Java-call reachability when Norm invokes components dynamically. The application frontend build owns configuration. Resource packaging and dual-host acceptance are covered by FrontendPackagingTest (`norm-vaadin/profile-frontend/src/test/java/dev/normlanguage/example/profile/FrontendPackagingTest.java`), Jetty custom-component acceptance (`norm-vaadin/standalone/src/test/java/dev/normlanguage/vaadin/jetty/CustomComponentBrowserTest.java`), and Spring custom-component acceptance (`norm-vaadin/spring/src/test/java/dev/normlanguage/vaadin/spring/CustomComponentBrowserTest.java`).

Validate JVM execution, production resources, and Native delivery separately. Spring Native follows the official AOT path. Standalone Jetty Native separately validates reflection, resources, initialization, and push. Record unverified paths accurately; loading a JAR or starting a server does not establish complete delivery.

Analyzable factory methods provide Spring bean definitions. Runtime page factories enter through the application context's resolvable dependencies. SpringAotTest (`norm-vaadin/spring/src/test/java/dev/normlanguage/vaadin/spring/SpringAotTest.java`) covers official AOT generation, proxy bytecode in compilation, no page construction during builds, and browser operation/shutdown in a separate JVM with generated code enabled. It does not establish Norm Native execution support.

AOT generation runs in a separate JVM to prevent already-loaded runtime proxies from affecting build output. The build entry point is VaadinSpringAotProcessor (`norm-vaadin/spring-aot/src/main/java/dev/normlanguage/vaadin/aot/VaadinSpringAotProcessor.java`); NormSpringApplication (`norm-vaadin/spring/src/main/java/dev/normlanguage/vaadin/spring/NormSpringApplication.java`) defines application identity. The application artifact (`norm-vaadin/profile-spring/build.gradle.kts`) carries generated classes, resources, and Native hints into builds through ordinary pinned Norm dependencies. The compiler core gains no Spring-specific conditions. AotPackagingTest (`norm-vaadin/profile-spring/src/test/java/dev/normlanguage/example/spring/AotPackagingTest.java`) defines archive boundaries.

Adapter build declarations are the sole source of version selection. Pin exact artifacts and content; do not use floating versions or rewrite published artifacts. Dependency upgrades require checking the compatible combination of Vaadin, Servlet environment, Spring Boot, and Jetty.

## Implementation stages and completion criteria

| Stage | Work | Completion criteria |
| --- | --- | --- |
| P0 | Verify facts, module boundaries, pinned dependencies, and test entry points | Valid design links; no ui dependency or unrelated std changes; targeted failing tests can run |
| P1 | Bridge field observation to official Signals | Real Signals tests pass for branch dependencies, object isolation, unsubscription, and event-batched refresh |
| P2 | Norm content builders, property closures, two-way input | Ordinary-field examples compile/run; nested function reads, programmatic write-back, input events, and component identity pass tests |
| P3 | Standalone Jetty package and Vaadin installation | Production pages work in a browser; two sessions are isolated; port conflicts and normal shutdown release resources |
| P4 | Dynamic regions, keyed lists, page tasks | Branch resources are released, reordering preserves identity, stale asynchronous results are invalidated, and UI scheduling passes acceptance |
| P5 | Spring foundations and official Vaadin Spring integration | The same page runs under Boot; real beans/proxies, scopes, startup, and shutdown pass acceptance |
| P6 | Shared resource builds and delivery | Complete standard components and custom resources; independent startup needs no development server; Native paths tested separately |
| P7 | Code review and documentation | No duplicate type authorities, lifecycle races, meaningless helpers, or process comments; explicit support matrix and remaining limitations |

Write behavioral tests before implementing each stage, without running the full test suite. Lower-level tests use real Vaadin/Jetty/Spring objects. Norm tests exercise actual host bindings, and browser acceptance uses real input and clicks. HTTP 200 is not a substitute for UI acceptance.

Test reports and browser acceptance artifacts belong to their adapter modules. This document does not duplicate test counts or implementation logic. The implementation Goal tracks stage completion and is marked complete only after all required work is done.

The adapter workspace README (`norm-vaadin/README.md`) owns the measured support matrix and acceptance entry points. A Native artifact that builds but fails to start does not count as runtime support.

## Official references

- [Vaadin compatibility](https://vaadin.com/docs/latest/compatibility)
- [Component Signals bindings](https://vaadin.com/docs/latest/flow/ui-state/building-ui)
- [Computations and dependency tracking](https://vaadin.com/docs/latest/flow/ui-state/effects-computed)
- [Production frontend builds](https://vaadin.com/docs/latest/flow/production/production-build)
- [Vaadin Spring integration](https://vaadin.com/docs/latest/flow/integrations/spring)
- [Vaadin Spring scopes](https://vaadin.com/docs/latest/flow/integrations/spring/scopes)
- [Spring Boot container configuration](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Jetty 12.1 Servlet environments](https://jetty.org/docs/jetty/12.1/programming-guide/migration/12.0-to-12.1.html)
- [Vaadin Native builds](https://vaadin.com/docs/latest/flow/production/native)
