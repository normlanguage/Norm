package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaApiField(
    String owner,
    String name,
    String descriptor,
    JavaTypeSignature type,
    int modifiers,
    Optional<Object> constantValue,
    List<JavaApiAnnotation> annotations,
    List<JavaApiTypeAnnotation> typeAnnotations,
    JavaApiDisposition disposition,
    Optional<JavaApiIssue> issue,
    List<JavaBindingCallable> bindings) {
  public JavaApiField {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(constantValue, "constantValue");
    annotations =
        annotations.stream().sorted(Comparator.comparing(JavaApiAnnotation::type)).toList();
    typeAnnotations =
        typeAnnotations.stream()
            .sorted(Comparator.comparingInt(JavaApiTypeAnnotation::typeReference))
            .toList();
    Objects.requireNonNull(disposition, "disposition");
    Objects.requireNonNull(issue, "issue");
    bindings = List.copyOf(bindings);
    if ((disposition == JavaApiDisposition.UNSUPPORTED) != issue.isPresent()) {
      throw new IllegalArgumentException("unsupported fields must have exactly one issue");
    }
    if ((disposition == JavaApiDisposition.BINDABLE) != !bindings.isEmpty()) {
      throw new IllegalArgumentException("bindable fields must have at least one binding");
    }
  }
}
