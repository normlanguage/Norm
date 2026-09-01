package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaApiMethod(
    String owner,
    String name,
    String descriptor,
    JavaMethodSignature signature,
    int modifiers,
    JavaCallableKind kind,
    List<String> exceptions,
    List<JavaApiAnnotation> annotations,
    List<JavaApiTypeAnnotation> typeAnnotations,
    List<JavaApiParameter> parameters,
    Optional<JavaAnnotationValue> annotationDefault,
    JavaApiDisposition disposition,
    Optional<JavaApiIssue> issue,
    Optional<JavaBindingCallable> binding) {
  public JavaApiMethod {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(kind, "kind");
    exceptions = exceptions.stream().sorted().toList();
    annotations =
        annotations.stream().sorted(Comparator.comparing(JavaApiAnnotation::type)).toList();
    typeAnnotations =
        typeAnnotations.stream()
            .sorted(Comparator.comparingInt(JavaApiTypeAnnotation::typeReference))
            .toList();
    parameters =
        parameters.stream().sorted(Comparator.comparingInt(JavaApiParameter::index)).toList();
    Objects.requireNonNull(annotationDefault, "annotationDefault");
    Objects.requireNonNull(disposition, "disposition");
    Objects.requireNonNull(issue, "issue");
    Objects.requireNonNull(binding, "binding");
    if ((disposition == JavaApiDisposition.UNSUPPORTED) != issue.isPresent()) {
      throw new IllegalArgumentException("unsupported methods must have exactly one issue");
    }
    if ((disposition == JavaApiDisposition.BINDABLE) != binding.isPresent()) {
      throw new IllegalArgumentException("bindable methods must have exactly one binding");
    }
  }
}
