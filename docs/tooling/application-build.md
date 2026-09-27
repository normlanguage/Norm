---
title: Application Builds
description: Build Norm applications as self-contained executables
---

# Application Builds

`norm build` uses GraalVM Native Image by default to compile the application, fixed-version NARs, Java artifacts, and required runtime code into a native executable. Dependency resolution, type checking, and closed-world linking happen during the build. The resulting program starts directly; at runtime it does not unpack a JVM, contact GitHub, Maven, or Gradle, or require Norm or Java on the target machine.

The first `norm setup` installs the Norm Native Image toolchain under `~/.norm/toolchains/native-image`. If setup is skipped, the first `norm build` installs it automatically. The download uses the official GraalVM Community version pinned by Norm and is SHA-256 verified before extraction. Later builds reuse the same toolchain.

## Single-file applications

Run from the source file's directory:

```text
norm build web.norm
```

On Windows, the output is `web.norm.exe` beside the source. On Linux and macOS, it is `web` beside the source.

A Windows single-file build generates only an EXE beside the source, without a `.norm` or `build` directory or loose DLLs. When companion libraries are required, the EXE embeds the complete native runtime payload. On first launch it verifies and extracts that payload to `~/.norm/cache/native-applications/<content-hash>`; later launches reuse it. The launcher contains neither the JVM nor the Norm compiler and does not change the working directory, program arguments, or exit code. This is single-file delivery, not fully static linking; reports measure EXE size and native-image size separately.

Linux and macOS still deliver required companion libraries according to the GraalVM artifact manifest. The build report lists actual runtime files, hashes, and total size; copy them together when deploying.

Dependencies and the toolchain use user-level caches. Annotation-processing and native-build intermediates belong to the build session and are cleaned after success or failure. Business data deliberately created by the program, such as databases and logs, is not a build temporary and is not deleted automatically.

`std.application.applicationDirectory()` returns a typed `std.filesystem.Path`: in a packaged application it points to the public EXE's directory, while in source execution it points to the entry source's directory. It is unaffected by the internal extraction cache or the launch command's working directory. Explicit relative file paths still resolve from the current working directory.

## Projects

A project uses `module.norm` and `application.norm` in its root package as entry points. Run in the project directory:

```text
norm build
```

Or explicitly select the project directory:

```text
norm build .
```

Output goes to `<module>/build/<artifact>[.exe]`. `artifact` derives from the Module repository coordinates; for example, `hello.web` produces `build/web.exe` on Windows.

## Build targets

For a Windows desktop application, `norm build --windowed <project-directory>` creates a native EXE without a command-line window. It also works with an explicitly selected `--jvm` target. Command-line applications remain the default. Standard-output and error logs for windowed mode are under `%LOCALAPPDATA%/Programs/Norm/logs/<application-name>/<start-time>-<process-id>/`. See [WindowsSubsystem](../../cli/compiler/src/main/java/dev/w0fv1/norm/build/WindowsSubsystem.java) and [ApplicationProcess](../../cli/launcher/Norm.Launcher/ApplicationProcess.cs).

By default, build logs show target, dependency resolution, NAR cache path, compilation, annotation processing, packaging phases, and total elapsed time. A repository request failure retains its URL and underlying exception chain. `Built` appears only after the artifact is written successfully.

The default target is `native`. To diagnose JVM behavior, select it explicitly:

```text
norm build --jvm web.norm
```

`--jvm` is a development and compatibility target and does not change default publication semantics. The first complete Native Image build of a framework application may take several minutes; that is a build-time cost. The generated program no longer pays for JVM startup, dependency resolution, or unpacking at launch.

JVM artifacts contain the compiled Core program, Java classes, resources, dependencies, and JVM. Startup loads the executable artifact directly without reparsing application sources or initializing the compiler. It shares the [PreparedApplication](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/PreparedApplication.java) runtime entry point with artifacts prepared by `norm run`. See [Startup Performance](/design/startup-performance) for input reuse and invalidation boundaries.

[JavaModulePath](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaModulePath.java) derives build-time module reads for Native Image features in explicit Java modules.

Native Image also requires an operating-system C toolchain: Visual Studio 2022 C++ Build Tools with the Windows SDK on Windows, GCC and system development libraries on Linux, and Xcode Command Line Tools on macOS. Norm manages GraalVM but does not implicitly modify those system development tools.

See the [release process](/design/release-process) for supported platforms and verification rules.

## Size reports

Native builds automatically prune unreachable Core definitions and Java calls. Runtime linking and reflection configuration use the same retained set; users do not write pruning allowlists. Framework-generated types are retained as external entry points, annotations are retained with their owning declarations, and all bound calls are retained when a dynamic Java call name cannot be determined statically. Java type mappings, framework resources, and service-discovery configuration are currently retained conservatively. `CoreReachability` and `CoreReachabilityTest` are the implementation and test entry points.

`CoreWalker`, `CoreDependency`, and `CoreTree` jointly provide Core dependency classification and declaration/body traversal, recording direct, virtual, and interface calls separately. Traversal of built-in operations covers expressions, collection materialization, indexed reads and writes, iteration, and interface implementations; a built-in implementation in a declaration does not mean it was executed. Runtime materialization types needed by host calls enter the same Core dependency graph through `BoundIntrinsic.runtimeDependencies`, so applications need no extra reference to exception types solely to prevent pruning. See `CoreDependencyTest`, `CoreReachabilityTest`, and `CoreTraversalContractTest` for boundary checks.

Core artifacts retain complete definition groups. `CoreExecutionPlan` selects callable members and dispatch slots over them for both Java call filtering and the Lowerer, and is archived into Hosted preparation during Native builds. Unselected methods do not generate execution nodes, and unrequested dispatch slots do not enter the runtime table, although declaration signatures can remain. Ordinary reference-type constructors are selected by execution need. Constructors for values, annotations, and exceptions needed for runtime materialization remain conservative, and reflective constructor enumeration retains the complete constructor set. Type structure and all possible implementations of requested slots remain; selection does not yet tighten further by receiver instantiation. Reflective method enumeration conservatively activates the full dispatch set, and annotation lifecycles use existing Core protocols. See `ExecutionSelectionTest` for node and runtime-table selection, external entry points, and cache isolation; `CoreReachabilityTest` for dynamic Java calls; and `ApplicationProgramArchiveTest` for archive consistency.

The execution graph is prepared at build time, and built-in operations bind at their actual call sites, so Core-to-execution-node conversion does not run at startup. User code and environment-dependent services still execute and initialize after startup. See `NativeApplicationFeature`, `TruffleExecutionBackend.prepare`, `IntrinsicOperation`, and `PreparedExecutionTest`.

HTTP, file, byte-I/O, time, and Java-task built-ins bind individually in each Dispatcher's `resolve`, rather than selecting a whole operation family by opcode at runtime. Exception conversion still uses a shared execution boundary. `IntrinsicOperationTest` checks structure; `HttpClientTest`, `FileSystemTest`, `ByteStreamTest`, and `TimeIoFoundationTest` check requests, files, clocks, and resources; `JarBindingConcurrencyIntegrationTest` checks task callbacks. Other operation families follow their respective dispatch implementations.

Standard Java services are handled by GraalVM's ServiceLoader Feature; framework-specific services use the Native Features shipped with those frameworks. Norm no longer duplicates reflection-constructor registration or blanket service-resource globs for every service, and its Native archive carries no service-scan table. Explicit application resources are retained by concrete path; `*` in a name does not expand into a query wildcard. `NativeApplicationFeature` registers callback proxies and the application execution bridge. Processor discovery at build time retains a separate `JarServiceIndex` and does not replace runtime service handling.

Build archives and runtime entry points are separate. A runtime entry holds the execution graph, call table, type links, and application package name, not the entire set of `LinkedJarBinding` build descriptions or a service-scan index. `LinkedJarBinding.linkCalls` links the call table and rejects duplicate call identities; runtime type and enum recognition still use the full linked type table. `NativeApplicationProgramTest` and `LinkedJarBindingTest` verify boundaries and execution. `RuntimeSourceMap` and `RuntimeDeclarationIndex` provide exception locations and annotation declaration names without retaining full source maps for those features; `RuntimeSourceMapTest` checks derivation consistency. The execution entry point, reflection, and Java bridge share structural declarations, function signatures, and annotation policy from `RuntimeProgram`, without keeping function bodies and local variable tables through that index; see `RuntimeProgramTest`. Types and annotations still reuse Core data types; this is not removal of the whole Core model.

`LinkedJavaClasses` resolves known Java types in one place. The JVM resolves them after constructing the application class loader; Native resolves them during Hosted preparation and retains an immutable result. Each execution still creates separate class and reverse-mapping caches. Linking types does not initialize user classes; see `LinkedJavaClassesTest`. Dynamic application types and callback interfaces continue to resolve under their own contracts, so runtime class lookup is not eliminated altogether.

`NativeImageConfigurationWriter` derives package-level annotation queries for generated application classes from class names and deduplicates them by package. A missing `package-info` retains normal absence semantics and does not cause a placeholder class to be generated; this query declaration exposes no additional member calls. See `NativeImageConfigurationWriterTest` for named packages, nested classes, and the unnamed package.

When a Norm object materializes into Java, its allocation declaration derives from the intersection of Core class/value definitions and actually generated types, without rerunning a Norm constructor. Interfaces, enums, annotations, synthetic containers, and types not generated receive no such declaration. See `NativeImageConfigurationWriterTest` for the boundary and `JavaApplicationDispatch` for materialization.

When Java constructs a Norm object, `JavaApplicationDispatch` invokes the linked constructor ID, separating allocation from initialization. Argument conversion is shared with ordinary method calls rather than reselecting overloads by parameter count. `JavaAnnotationBindingIntegrationTest` checks constructors with the same arity but different parameter types.

`JvmJarBindingRuntime` pairs direct JAR call targets with argument/return conversion in advance as immutable `LinkedCalls`. The JVM prepares them during linking; Native prepares them during Hosted preparation. Runtime no longer chooses reference-type conversion from complete Java type descriptions. Optional and Task results and bidirectional callbacks recursively reuse the same selection entry point; class-lookup caches and resource state still belong to each execution. Basic numeric conversions retain range checks. See `JvmJarBindingRuntimeTest`, `JarBindingConcurrencyIntegrationTest`, and `NativeApplicationProgramTest`.

`JavaApplicationCallLinker` links the application call table and supplies it to the execution bridge through `JavaApplicationRuntime`. The JVM prepares it after constructing the application class loader; Native prepares it during Hosted preparation. Native runtime no longer creates a call registry reflectively; framework reflection remains separate. `JavaApplicationCallLinkerTest` covers immutable sharing, close isolation, and applications without generated types; `JavaDirectCallBundleTest` covers target initialization timing.

`JavaApplicationMethodIndex.analyze` derives both framework execution entry points and Java instance-call targets from `NormApplicationMethod` on compiled classes. Constructors and static methods are entry points; abstract methods are not. ApplicationRunner run/test paths and Native builds pass these entry points explicitly to the execution plan through `ExecutionBackend`; Native also uses them for Core retention analysis. Generated types are still retained conservatively, which does not imply precise framework-member pruning. `JavaApplicationMethodIndexTest` and `JavaAnnotationBindingIntegrationTest` check the boundary and real annotation-processor output.

Native-build toolchain dependencies are selected by execution and Hosted-purpose closure, preserving shared dependencies and verifying release-content hashes. Application dependencies and generated code are unaffected by this filter. See `NativeToolchainClasspath` for selection and the [compiler build logic](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/gradle/NormCompilerPlugin.java) for purpose declarations. A dependency needed during Hosted building is not necessarily runtime code in the final EXE.

During project compilation, toolchain physical artifacts and the application graph use the same `JarBindingClasspath` version selection. `CompiledApplication` holds the result. Annotation-processing compile and processor paths, and Native service scanning, call bridging, external Graal metadata, and classpath all derive from that plan rather than selecting versions again at packaging time. Build-time bridge loading is isolated from the compiler class loader so an older parent-loader version cannot shadow the selected dependency. Annotation processing and Native still run in separate processes with their own lifecycles.

`components` in the catalog lists the logical components contained in each physical JAR. Using any component retains its carrier artifact and related dependencies. Merge relationships share the actual JAR-merge build declaration; missing files are not used to infer that a component has no code.

If a retained merged JAR and an application JAR provide the same primary artifact component, the build reports a physical ownership conflict rather than treating equal coordinate versions as equal content. `JarArtifactOwnership` defines this rule; artifacts with different classifiers still have separate identities.

Native builds automatically print file size, machine code size, image heap size, and reachable method count. By default, only the latest result remains at `~/.norm/cache/build-reports/<output-path-hash>/latest.json`; intermediates are cleaned with the build, and a failure does not reuse a previous success result.

Run `norm build web.norm --diagnostics` for full diagnostics. Detailed reports are saved under a separate `run-*` directory in the same user-level location; the build log gives its full path. Call trees, heap breakdown, and input fingerprints are generated only when explicitly enabled. CI enables this option to collect evidence without depending on the source-directory layout. The implementation entry points are `NativeBuildReport`, `TemporaryDirectory`, `NativeApplicationDelivery`, and `cli/launcher/Norm.NativeHost`.

- `size.json`: EXE path and SHA-256 of a successful build, plus core size metrics. `runtimeFiles` and `deliveryBytes` describe the complete runtime-file list and total byte count; `executableBytes` measures only the EXE.
- `build-artifacts.json`: the raw artifact list for the pinned GraalVM version, including runtime and diagnostic files. Relative paths are based on the build staging directory, which is cleaned afterward. `runtimeFiles` identifies actual delivery locations.
- `build-output.json`: raw structured Native Image statistics, including toolchain, optimization level, and analysis results.
- `dashboard.dump`: JSON code and heap-object detail generated by Native Image.
- `core-retention.json`: first retention rule, predecessor group, declaration names, and built-in operations involved in declarations or bodies for Core definition groups. Links use `dependency` to distinguish types, construction, direct calls, virtual calls, interface dispatch, closures, member declarations, and implementations, based on the shared `CoreWalker`/`CoreDependency` classification. A predecessor chain traces back to an application or framework entry point. This attributes current conservative linking; it is neither exact execution demand nor every retention path. `CoreReachability.analyze` is the implementation entry point.
- `analysis/`: GraalVM native diagnostics, including CSV call relationships, class initialization, and substitutions, for investigating retention. The pinned toolchain determines filenames and formats.
- `native-image.args`: arguments actually passed to Native Image for checking classpath and compiler options. Temporary input paths in this file expire after the build; it is not a replayable build script.

- `build-inputs.json`: original arguments before submission, ordered classpath, application archive, and Native Image startup-file fingerprints. For directories, all files are recorded by relative path, SHA-256, and byte count, including generated classes and configuration. It shares one argument/classpath construction entry point with the arguments file. This is not a snapshot of the entire GraalVM/C toolchain or environment variables, and excludes options appended after script startup. See `NativeBuildReportTest` for generation and `native-build-inputs.test.mjs` for archive-format verification.
- `toolchain-artifacts.json`: dependency coordinates, filenames, content hashes, roots, purposes, and selected-version dependency edges derived from the graph Gradle actually resolved and shipped with Norm. It identifies toolchain inputs, not application runtime dependencies or code all included in the EXE. See the [compiler build logic](../../gradle/build-logic/src/main/java/dev/w0fv1/norm/gradle/NormCompilerPlugin.java) for purpose declarations.
- `java-artifacts.json`: artifact identities, paths, SHA-256 hashes, and byte counts selected by the unified Java linking plan. Contents are verified again before generation, and a mismatch from resolution time aborts the build. This contains selected application and toolchain Java artifacts, not all code entering the EXE, generated application classes, or utility-class directories outside the artifact graph.
- `build.log`: Native Image phase log, including GraalVM image-heap partition sizes, retained even on failure. It excludes the earlier Norm compilation phase. Partition sizes and the object total in `dashboard.dump` help distinguish object data from heap-layout overhead; they are not interchangeable.
- `application-methods.json`: mapping from Norm method IDs derived from compiled application classes to Java declaring types, method names, and JVM signatures. It shares `JavaApplicationMethodIndex` with the direct application-call table produced during annotation processing. Proxy calls do not scan method annotations; this index does not determine a framework's own reflection needs.
- `reachability-sources.json`: selected dependency versions, metadata version, index and source-file hashes, tested-version matches, and `override` declarations from the official metadata repository shipped with Norm. `versionTested` means only that the repository lists that tested version, not that application behavior is guaranteed. An absent source directory is recorded as such with an empty file list. This report does not contain all metadata embedded in JARs or generated by processors, and does not claim that a configuration was activated in Native Image.

The native-acceptance archive entry point `cli/compiler/scripts/verify-native-size.mjs` requires a complete Java input list and rejects duplicate identities, invalid hashes, and invalid sizes. Archived evidence can be checked without the original cache paths remaining accessible. Those paths trace build inputs; their contents are verified during the build.

[The examples repository](https://github.com/normlanguage/examples/tree/main/scripts) maintains Web/ORM acceptance and performance measurement.

`verify-native-execution.mjs` verifies complete delivery of a command-line sample and runs it three times in isolation, then writes `execution-verification.json` on success. On failure, it retains the execution copy and `execution-failure.json` in the archive directory and produces no success receipt. Native size and Release workflows upload `build/reports/native-size` as a separate diagnostic artifact; the workflow defines naming and retention. Uploading evidence is not an automatic size-budget gate.

An archived functional-acceptance report can be checked against a size budget:

Both acceptance and comparison require a valid `build-inputs.json`; they do not manufacture fingerprints for old reports. A comparison's `passed` means the delivery-size budget held within an existing comparable application/toolchain scope. `identicalBuildInputs` and `buildInputChanges` separately report whether submitted arguments, classpath content, archive, and startup files changed. Classpath order is retained, separately recorded historical root paths do not enter content comparison, and argument text is not path-normalized. Passing a budget does not mean inputs were identical or prove that one change caused a size benefit. Archive reading checks structure without requiring cleaned original directories to still exist. See `compare-native-size.test.mjs`.

```text
node cli/compiler/scripts/compare-native-size.mjs <baseline-report> <candidate-report> [maximum-growth-bytes]
```

Use `compare-native-size.mjs --sets <baseline-root> <candidate-root> [maximum-growth-bytes]` for whole report sets. Every direct child of both directories must be a complete report. Reports match by verified input and functional scope, not a random directory name. Each candidate must have exactly one baseline and complete sample coverage. Empty sets, ambiguous baselines, duplicate candidates, missing samples, and incomparable inputs fail. The budget applies per sample; a decrease in one cannot offset growth in another.

`native-size-gate.mjs` is the Native size baseline entry point; `native-baseline.mjs` and `native-baseline-store.mjs` define policy and persistence. When no baseline exists, only complete acceptance on the main repository's default branch can propose one. A separate write job saves it after the test job succeeds, marking it `initialized` without claiming a passed comparison. Pull requests read baselines only and fail explicitly if one is missing; they receive no repository write permission.

Baselines live at `refs/heads/ci/native-size/<workflow-id>/<platform>` and contain only JSON reports needed for comparison and provenance, not EXEs, JARs, or large call graphs. They do not expire with Actions diagnostic attachments. Later runs compare against that fixed baseline; they do not automatically raise the budget. Damaged evidence and incomparable inputs are not treated as first runs. To reset deliberately, manually run Native size on the default branch with `native_baseline_reset_reason`. A reset keeps Git history and provenance and uses the expected parent commit to guard concurrent writes. Actions summaries distinguish pending initialization, initialization, reset, pass, growth, and evidence/input errors. Release continues to archive evidence without running this baseline gate. See `native-baseline-policy.test.mjs` and `native-baseline-store.test.mjs`.

The default allowed growth is zero, measured by complete delivery size. Exceeding budget or incomparable evidence exits nonzero; EXE, code section, and image heap deltas are also reported. The comparator checks recorded toolchain, optimization level, source hashes, Java artifact content, and at least three functional runs of equal scope. Java input validation and archival share `native-java-inputs.mjs`; summarized metrics and raw GraalVM statistics share `native-size-metrics.mjs`. This check does not rerun historical programs or prove a fully reproducible build: generated classes, all metadata, and extra compiler arguments do not yet have one unified input fingerprint, so changes to those inputs still require independent review.

These JSON files are generated analysis artifacts, not project configuration, and do not participate in program execution. Reports are not cleaned automatically and can be archived or deleted as needed. Analysis should verify the EXE hash and keep platform, toolchain, and optimization arguments consistent. On-disk file size is not prelink image size, and JAR size is not its contribution to the EXE. See [Native Image build output](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/) for raw metric definitions.

The caller closes compiled application artifacts. The [compiler architecture](/spec/compiler-design#application-and-artifact-boundaries) describes the shared boundary for JAR/resource capture, method indices, and runtime resources. Implementation entry points are [`ApplicationCompiler`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java) and [`ApplicationBundleWriter`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/build/ApplicationBundleWriter.java).
