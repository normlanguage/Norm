package dev.w0fv1.norm.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record JavaAnnotationProcessingOutput(
    Path root, List<JavaAnnotationStub> stubs, JavaApplicationMethodIndex.Analysis methods) {
  public JavaAnnotationProcessingOutput {
    root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    stubs = List.copyOf(stubs);
    Objects.requireNonNull(methods, "methods");
  }

  public Path classes() {
    return root.resolve("classes");
  }

  public Path generatedSources() {
    return root.resolve("generated-sources");
  }
}
