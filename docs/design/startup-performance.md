---
title: Application Startup Performance
description: Workload, artifact-reuse boundaries, and acceptance for running source programs
---

# Application Startup Performance

The goal is to reduce actual work in package consumption, application preparation, and JVM startup. Stable Java adaptation data is prepared when a package is published; application-specific data is linked during its build; process state is created at runtime. Source execution and JVM builds share execution artifacts and retain one Core/Truffle execution path. User entries remain `norm package`, `norm run`, and `norm build`; they do not require example scripts or a background daemon.

## Measurement

Measure first runs, repeated runs, and runs after source edits for Jetty, standalone Vaadin Todo, and Spring Web + Vaadin. Compare Java programs and Norm JVM artifacts using the same JDK, dependency versions, and framework configuration. Separate process creation, first progress, application entry, and HTTP readiness; test browser interactions independently. Run each set at least three times, retaining raw results and medians. Measure dependency downloads separately from local-cache scenarios.

[measure-startup.mjs](../../cli/compiler/scripts/measure-startup.mjs) is the measurement entry and records commands, working directories, raw logs, first output, and HTTP response times. Use `--command`, `--cwd`, and `--output` for the measured target and report, followed by application arguments after `--`. The measured program should print its local HTTP address; the measurer ends the process tree it creates.

## Implementation order

1. Freeze runtime libraries, CLI, packages, and test inputs, retaining a same-JDK comparison. Establish workload tests for changing a function body, changing a signature, changing a resource, moving source, and running separate processes.
2. Establish a module compilation contract that does not require an application entry. Complete business semantic checks before publication; module artifacts retain public declarations, Core, dependency identity, and source mappings. A broken module cannot publish successfully.
3. Have source and archives consume the same declaration contract and treat module dependencies as compilation boundaries. The standard library and formal packages reuse the same mechanism; generics, default parameters, annotations, and Java bindings obtain their data from the same compilation result.
4. Connect cross-process incremental history to downstream builds. Reanalyze changed declarations and required dependencies, directly reusing unchanged modules and Core definitions. Invalidate source locations separately from implementation content and use current mappings for diagnostics. Distinguish declaration ABI changes from implementation changes: relinking execution after a function-body edit must not automatically rebind or convert all callers.
5. Unify artifact capture, dependency selection, and materialization within the build transaction. Refer to stable dependencies, generated classes, and resources by content. Check a lightweight input index before loading application-cache artifacts. Coordinate production by content key, with short publication and reclamation critical sections.
6. Finish optimization at the boundary of importing published Core and reusing caller links. Complete the three real examples, browser interactions, Java callbacks, concurrent-cache tests, and CLI installation acceptance, then review and commit in stages. Do not expand unrelated initialization and class-loading hotspots into new optimization tasks.

Accept each optimization against both workload and final latency. Check whether package consumption repeats scans or adapter generation, warm startup copies dependencies, and execution preparation traverses or loads fewer types. Reducing an internal stage while regressing overall startup or application semantics is not completion.

Modified-source acceptance uses separate processes and records the counts of parsed documents, analyzed declarations, Core actually generated, and dependencies copied. Identical Core identity or a historical report's total definition count does not prove the computation was avoided in this run. Published packages need no reanalysis of dependency source; unrelated resources and Java bridge artifacts should remain reusable.

## Correctness boundaries

Cache identity must cover compiler and ABI, source content and location, module-resolution inputs, NAR/JAR content, resources, compiler options, and processor inputs. Added and deleted files also change identity. Do not compare only modification time or reuse stale diagnostic locations or resources.

Dynamic module declarations and external inputs read by annotation processors are not pure functions by default. Reuse only artifacts whose full input boundary can be expressed; untracked inputs must be reevaluated. Concurrent writes publish atomically, reads verify content, failures publish no successful artifact, and runtime state or class loaders never enter persistent caches.

Acceptance includes repeated hits across processes, source/dependency/resource changes, same-timestamp content changes, added and deleted files, corrupt caches, concurrent runs, and diagnostic locations. Run related tests and real examples; do not skip validation to improve timing.

## Implementation entries

- [PreparedApplication](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/PreparedApplication.java): shared execution entry for JVM artifacts and the fast source path.
- [ApplicationProgramPlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationProgramPlan.java): reachability and call-retention plan shared by JVM and Native.
- [PreparedApplicationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/PreparedApplicationCache.java): reads an input index before execution artifacts and invalidates module reevaluation separately from the cache body.
- [DirectoryArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/DirectoryArtifactCache.java) and [DirectoryArtifactCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/DirectoryArtifactCacheTest.java): atomic publication, process ownership, capacity reclamation, and corruption repair for application files.
- [ArtifactFileSet](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/ArtifactFileSet.java) and [ResolvedJarClasspathTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ResolvedJarClasspathTest.java): dependency-file content identity, classpath ownership, and reuse after change; no cache directory is created without Java dependencies.
- [PublishedJarBinding](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/PublishedJarBinding.java) and [ModulePackagerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/ModulePackagerTest.java): published-binding content, ABI, dependency graph, and source consistency.
- [CompiledModule](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompiledModule.java) and [PublishedCoreImportTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PublishedCoreImportTest.java): module Core imports, relinking dependency implementations, and content identity of cross-process caches.
- [AnalysisJournal](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/AnalysisJournal.java) and [AnalysisTransactionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/AnalysisTransactionTest.java): incremental rollback and nested transactions for semantic probes.
- [FileSnapshot](../../cli/compiler/src/main/java/dev/w0fv1/norm/value/FileSnapshot.java) and [FileSnapshotTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/value/FileSnapshotTest.java): verify content during copying; failures do not replace the target file.
- [ProjectInputSnapshot](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ProjectInputSnapshot.java): content validation for source, discovery boundaries, resources, and dependencies.
- [PreparedApplicationCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationCacheTest.java) and [PreparedApplicationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/application/PreparedApplicationTest.java): fast-path invalidation and execution detached from original source.

- [Compiler architecture](/spec/compiler-design): existing incremental model and content identity.
- [ApplicationRunner](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationRunner.java): application assembly and execution.
- [ApplicationCompiler](../../cli/compiler/src/main/java/dev/w0fv1/norm/application/ApplicationCompiler.java): compilation and generated Java artifacts.
- [CompilerSession](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilerSession.java): compilation sessions and declaration history.
- [DeclarationIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/DeclarationIdentity.java) and [PortableDeclarationIdentityTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PortableDeclarationIdentityTest.java): declaration identity, private types, and generic parameter consistency between publisher and consumer directories.
- [CoreBindingShape](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreBindingShape.java) and [DefaultArgumentContractTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/DefaultArgumentContractTest.java): parameter declarations, default-implementation linking, and persistent reuse; declaration signatures and default-implementation routing each contribute to identity.
- [CompilationResultCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CompilationResultCache.java): input identity and cross-session reuse of pure compilation results.
- [FileArtifactCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/FileArtifactCache.java): bounded storage, content validation, and concurrent publication.
- [PersistentCompilationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/frontend/PersistentCompilationTest.java): cross-session reuse, source changes, and corrupt-content validation.
- [CoreCompilationInput](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreCompilationInput.java) and [CoreCompilationInputTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreCompilationInputTest.java): mixed source/compiled-definition inputs, recursive groups, and transitive-dependency linking.
- [CoreReusePlan](../../cli/compiler/src/main/java/dev/w0fv1/norm/frontend/CoreReusePlan.java) and [CoreDependencyTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreDependencyTest.java): actual declaration dependencies, different call targets with identical content, and current source mappings.
- [CoreCanonicalizerTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/CoreCanonicalizerTest.java): canonical identity constraints for deep dependency graphs, single-member groups, and recursive symmetry.
- [JarResolver](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarResolver.java): Java dependency resolution.
- [JarGraphCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarGraphCache.java) and [JarApiCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JarApiCache.java): reuse of fixed dependency graphs and Java API-derived artifacts.
- [CompilerArtifactIdentity](../../cli/compiler/src/main/java/dev/w0fv1/norm/core/store/CompilerArtifactIdentity.java): compiler identity shared across derived caches.
- [PersistentModuleEvaluationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentModuleEvaluationTest.java): module functions still run on cache hits.
- [JavaCompilationCache](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaCompilationCache.java): source, classpath, and JDK toolchain identity for generated Java classes; integrated through [JavaAnnotationProcessorPipeline](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/JavaAnnotationProcessorPipeline.java).
- [JavaAnnotationBindingIntegrationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/JavaAnnotationBindingIntegrationTest.java): Java callback and annotation-processor execution boundaries after cache hits.
- [PersistentApplicationResourcesTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/PersistentApplicationResourcesTest.java): resource edits and deletions after compilation-cache hits.
- [FileArtifactCacheTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/core/store/FileArtifactCacheTest.java): concurrent independent processes and corrupt caches.
