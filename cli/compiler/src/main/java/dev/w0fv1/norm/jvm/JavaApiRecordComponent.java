package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record JavaApiRecordComponent(
    String owner,
    String name,
    String descriptor,
    JavaTypeSignature type,
    List<JavaApiAnnotation> annotations,
    List<JavaApiTypeAnnotation> typeAnnotations) {
  public JavaApiRecordComponent {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(type, "type");
    annotations =
        annotations.stream().sorted(Comparator.comparing(JavaApiAnnotation::type)).toList();
    typeAnnotations =
        typeAnnotations.stream()
            .sorted(Comparator.comparingInt(JavaApiTypeAnnotation::typeReference))
            .toList();
  }
}
