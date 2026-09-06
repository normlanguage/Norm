package dev.w0fv1.norm.project;

import dev.w0fv1.norm.jvm.JavaAnnotationProcessingOutput;
import dev.w0fv1.norm.value.CompilationResult;
import java.util.Objects;
import java.util.Optional;

public record ApplicationCompilation(
    ProjectSourceSet sourceSet,
    CompilationResult result,
    Optional<JavaAnnotationProcessingOutput> annotationOutput,
    dev.w0fv1.norm.jvm.JarBindingClasspath javaClasspath) {
  public ApplicationCompilation {
    Objects.requireNonNull(sourceSet, "sourceSet");
    Objects.requireNonNull(result, "result");
    annotationOutput = Objects.requireNonNull(annotationOutput, "annotationOutput");
    Objects.requireNonNull(javaClasspath, "javaClasspath");
  }
}
