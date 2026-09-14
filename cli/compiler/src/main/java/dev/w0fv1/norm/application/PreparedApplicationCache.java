package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.CompilerArtifactIdentity;
import dev.w0fv1.norm.core.store.DirectoryArtifactCache;
import dev.w0fv1.norm.core.store.FileArtifactCache;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessorPipeline;
import dev.w0fv1.norm.project.ModuleEvaluation;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.runtime.PreparedApplicationContent;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PreparedApplicationCache {
  private final FileArtifactCache indexes;
  private final FileArtifactCache contents;
  private final DirectoryArtifactCache directories;

  public PreparedApplicationCache(Path directory) throws IOException {
    indexes = new FileArtifactCache(directory, 32, 64L * 1024 * 1024);
    contents = new FileArtifactCache(directory.resolve("content"), 32, 512L * 1024 * 1024);
    directories =
        new DirectoryArtifactCache(directory.resolve("assets"), 32, 2L * 1024 * 1024 * 1024);
  }

  public DirectoryArtifactCache.Lease prepare(PreparedApplicationContent content)
      throws IOException {
    return content.acquire(directories);
  }

  public Attempt read(Path entry) throws IOException {
    if (!JavaAnnotationProcessorPipeline.cacheableEnvironment())
      return new Attempt(Optional.empty(), List.of());
    var bytes = indexes.read(key(entry));
    if (bytes.isEmpty()) return new Attempt(Optional.empty(), List.of());
    var cached = PortableObjectCodec.decode(bytes.orElseThrow(), Index.class);
    var initialInputs = cached.inputs();
    if (!cached.modules().isEmpty()) {
      var sources =
          cached.modules().stream()
              .map(module -> module.source().path())
              .collect(java.util.stream.Collectors.toSet());
      var files =
          cached.inputs().files().stream().filter(file -> sources.contains(file.path())).toList();
      if (files.size() != sources.size()) return new Attempt(Optional.empty(), List.of());
      initialInputs = new ProjectInputSnapshot(files, List.of(), cached.inputs().absent());
    }
    if (!initialInputs.matches()) return new Attempt(Optional.empty(), List.of());
    var evaluated = new ArrayList<ModuleEvaluation>();
    var backend = new NormRuntime();
    for (var module : cached.modules()) {
      var current = module.evaluate(backend);
      evaluated.add(current);
      if (!module.declaration().equals(current.declaration()))
        return new Attempt(Optional.empty(), evaluated);
    }
    if (!cached.modules().isEmpty() && !cached.inputs().matches())
      return new Attempt(Optional.empty(), evaluated);
    var content = contents.read(cached.content());
    if (content.isEmpty()) return new Attempt(Optional.empty(), evaluated);
    return new Attempt(
        Optional.of(
            PortableObjectCodec.decode(content.orElseThrow(), PreparedApplicationContent.class)),
        evaluated);
  }

  public void write(
      Path entry,
      CompiledApplication application,
      ProjectInputSnapshot inputs,
      List<ModuleEvaluation> modules)
      throws IOException {
    if (!JavaAnnotationProcessorPipeline.cacheableEnvironment()
        || !application.javaClasspath().processors().isEmpty()) return;
    if (modules.stream()
        .flatMap(value -> value.declaration().dependencies().stream())
        .anyMatch(value -> value.version().isEmpty())) return;
    if (!application.annotations().stubs().isEmpty()) {
      var files = new ArrayList<>(inputs.files());
      files.addAll(application.annotations().compilerInputs());
      inputs = new ProjectInputSnapshot(files, inputs.directories(), inputs.absent());
    }
    var captured = new PreparedApplicationWriter().capture(application);
    var dependencies =
        JarBindingClasspath.prepare(application.sourceSet().jarBindings()).artifacts().stream()
            .map(value -> new FileSnapshot(value.file(), value.content()))
            .toList();
    var content =
        new PreparedApplicationContent(captured.program(), captured.classes(), dependencies);
    var modulePrograms =
        modules.stream()
            .map(
                module -> {
                  var retained =
                      dev.w0fv1.norm.core.CoreReachability.analyze(
                              module.artifact(), java.util.Set.of())
                          .artifact();
                  return new ModuleEvaluation(
                      module.source(),
                      retained,
                      dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(retained),
                      module.declaration());
                })
            .toList();
    byte[] bytes = PortableObjectCodec.encode(content);
    var contentKey = Sha256Digest.compute(bytes);
    byte[] index = PortableObjectCodec.encode(new Index(inputs, modulePrograms, contentKey));
    if (inputs.matches()) {
      contents.write(contentKey, bytes);
      indexes.write(key(entry), index);
    }
  }

  private static Sha256Digest key(Path entry) throws IOException {
    return Sha256Digest.compute(
        new CanonicalWriter()
            .writeTag("prepared-application-index-1")
            .writeString(CompilerArtifactIdentity.current())
            .writeString(System.getenv().getOrDefault("JAVA_HOME", ""))
            .writeString(entry.toAbsolutePath().normalize().toUri().toString())
            .toByteArray());
  }

  public record Attempt(
      Optional<PreparedApplicationContent> content, List<ModuleEvaluation> modules) {
    public Attempt {
      modules = List.copyOf(modules);
    }
  }

  private record Index(
      ProjectInputSnapshot inputs, List<ModuleEvaluation> modules, Sha256Digest content) {
    private Index {
      java.util.Objects.requireNonNull(inputs, "inputs");
      java.util.Objects.requireNonNull(content, "content");
      modules = List.copyOf(modules);
    }
  }
}
