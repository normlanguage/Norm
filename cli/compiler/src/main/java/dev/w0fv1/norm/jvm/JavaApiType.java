package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JavaApiType(
    String binaryName,
    JavaApiTypeKind kind,
    int modifiers,
    JavaClassSignature signature,
    List<JavaApiAnnotation> annotations,
    List<JavaApiTypeAnnotation> typeAnnotations,
    Optional<String> enclosingType,
    List<JavaApiRecordComponent> recordComponents,
    List<String> permittedSubclasses,
    List<JavaApiField> fields,
    List<JavaApiMethod> methods,
    List<JavaApiMethod> inheritedMethods,
    JavaApiDisposition disposition) {
  public JavaApiType(
      String binaryName,
      JavaApiTypeKind kind,
      int modifiers,
      JavaClassSignature signature,
      List<JavaApiAnnotation> annotations,
      List<JavaApiTypeAnnotation> typeAnnotations,
      Optional<String> enclosingType,
      List<JavaApiRecordComponent> recordComponents,
      List<String> permittedSubclasses,
      List<JavaApiField> fields,
      List<JavaApiMethod> methods,
      JavaApiDisposition disposition) {
    this(
        binaryName,
        kind,
        modifiers,
        signature,
        annotations,
        typeAnnotations,
        enclosingType,
        recordComponents,
        permittedSubclasses,
        fields,
        methods,
        List.of(),
        disposition);
  }

  public JavaApiType {
    Objects.requireNonNull(binaryName, "binaryName");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(signature, "signature");
    annotations =
        annotations.stream().sorted(Comparator.comparing(JavaApiAnnotation::type)).toList();
    typeAnnotations =
        typeAnnotations.stream()
            .sorted(Comparator.comparingInt(JavaApiTypeAnnotation::typeReference))
            .toList();
    Objects.requireNonNull(enclosingType, "enclosingType");
    recordComponents =
        recordComponents.stream()
            .sorted(Comparator.comparing(JavaApiRecordComponent::name))
            .toList();
    permittedSubclasses = permittedSubclasses.stream().sorted().toList();
    fields =
        fields.stream()
            .sorted(
                Comparator.comparing(JavaApiField::name).thenComparing(JavaApiField::descriptor))
            .toList();
    methods =
        methods.stream()
            .sorted(
                Comparator.comparing(JavaApiMethod::name).thenComparing(JavaApiMethod::descriptor))
            .toList();
    inheritedMethods =
        inheritedMethods.stream()
            .sorted(
                Comparator.comparing(JavaApiMethod::name).thenComparing(JavaApiMethod::descriptor))
            .toList();
    Objects.requireNonNull(disposition, "disposition");
  }

  public List<JavaApiMethod> effectiveMethods() {
    return java.util.stream.Stream.concat(methods.stream(), inheritedMethods.stream())
        .sorted(Comparator.comparing(JavaApiMethod::name).thenComparing(JavaApiMethod::descriptor))
        .toList();
  }
}
