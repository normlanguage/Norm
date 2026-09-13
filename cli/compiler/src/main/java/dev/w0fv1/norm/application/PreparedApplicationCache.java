package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.CompilerArtifactIdentity;
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
  private final FileArtifactCache artifacts;

  public PreparedApplicationCache(Path directory) throws IOException {
    artifacts = new FileArtifactCache(directory, 32, 512L * 1024 * 1024);
  }

  public Attempt read(Path entry) throws IOException {
    if (!JavaAnnotationProcessorPipeline.cacheableEnvironment())
      return new Attempt(Optional.empty(), List.of());
    var bytes = artifacts.read(key(entry));
    if (bytes.isEmpty()) return new Attempt(Optional.empty(), List.of());
    var cached = PortableObjectCodec.decode(bytes.orElseThrow(), Entry.class);
    if (!cached.inputs().matches()) return new Attempt(Optional.empty(), List.of());
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
    return new Attempt(Optional.of(cached.content()), evaluated);
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
    if (!inputs.matches()) return;
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
    byte[] bytes = PortableObjectCodec.encode(new Entry(inputs, modulePrograms, content));
    if (inputs.matches()) artifacts.write(key(entry), bytes);
  }

  private static Sha256Digest key(Path entry) throws IOException {
    return Sha256Digest.compute(
        new CanonicalWriter()
            .writeTag("prepared-application-1")
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

  private record Entry(
      ProjectInputSnapshot inputs,
      List<ModuleEvaluation> modules,
      PreparedApplicationContent content) {
    private Entry {
      modules = List.copyOf(modules);
    }
  }
}
