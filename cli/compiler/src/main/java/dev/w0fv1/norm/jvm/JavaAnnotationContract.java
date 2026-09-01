package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.Type;

public record JavaAnnotationContract(
    Set<Target> targets,
    Retention retention,
    boolean inherited,
    Optional<String> repeatableContainer) {
  public JavaAnnotationContract {
    targets = Set.copyOf(targets);
    Objects.requireNonNull(retention, "retention");
    Objects.requireNonNull(repeatableContainer, "repeatableContainer");
  }

  public static JavaAnnotationContract from(JavaApiType type) {
    if (type.kind() != JavaApiTypeKind.ANNOTATION) {
      throw new IllegalArgumentException("Java type is not an annotation: " + type.binaryName());
    }
    Set<Target> targets = new LinkedHashSet<>();
    Retention retention = Retention.CLASS;
    boolean inherited = false;
    Optional<String> repeatableContainer = Optional.empty();
    boolean declaresTargets = false;
    for (JavaApiAnnotation annotation : type.annotations()) {
      switch (annotation.type()) {
        case "java.lang.annotation.Target" -> {
          declaresTargets = true;
          annotation.elements().stream()
              .filter(element -> element.name().equals("value"))
              .findFirst()
              .ifPresent(element -> addTargets(targets, element.value()));
        }
        case "java.lang.annotation.Retention" ->
            retention =
                annotation.elements().stream()
                    .filter(element -> element.name().equals("value"))
                    .map(JavaAnnotationElement::value)
                    .filter(JavaAnnotationEnumValue.class::isInstance)
                    .map(JavaAnnotationEnumValue.class::cast)
                    .map(value -> Retention.valueOf(value.constant()))
                    .findFirst()
                    .orElse(Retention.CLASS);
        case "java.lang.annotation.Inherited" -> inherited = true;
        case "java.lang.annotation.Repeatable" ->
            repeatableContainer =
                annotation.elements().stream()
                    .filter(element -> element.name().equals("value"))
                    .map(JavaAnnotationElement::value)
                    .filter(JavaAnnotationClassValue.class::isInstance)
                    .map(JavaAnnotationClassValue.class::cast)
                    .map(value -> Type.getType(value.descriptor()).getClassName())
                    .findFirst();
        default -> {}
      }
    }
    if (!declaresTargets) targets.addAll(Target.declarationTargets());
    return new JavaAnnotationContract(targets, retention, inherited, repeatableContainer);
  }

  public List<String> normTargetInterfaces() {
    List<String> result = new ArrayList<>();
    for (Target target : Target.values()) {
      if (!targets.contains(target)) continue;
      target
          .normInterface()
          .ifPresent(
              value -> {
                if (!result.contains(value)) result.add(value);
              });
    }
    return List.copyOf(result);
  }

  private static void addTargets(Set<Target> targets, JavaAnnotationValue value) {
    switch (value) {
      case JavaAnnotationArrayValue array ->
          array.values().forEach(element -> addTargets(targets, element));
      case JavaAnnotationEnumValue element -> targets.add(Target.valueOf(element.constant()));
      default -> throw new IllegalArgumentException("invalid Java @Target value " + value);
    }
  }

  public enum Target {
    PACKAGE("PackageTarget"),
    TYPE("TypeTarget"),
    ANNOTATION_TYPE("TypeTarget"),
    FIELD("FieldTarget"),
    CONSTRUCTOR("ConstructorTarget"),
    METHOD("FunctionTarget"),
    PARAMETER("ParameterTarget"),
    LOCAL_VARIABLE("LocalTarget"),
    TYPE_PARAMETER(null),
    TYPE_USE(null),
    MODULE(null),
    RECORD_COMPONENT(null);

    private final String normInterface;

    Target(String normInterface) {
      this.normInterface = normInterface;
    }

    public Optional<String> normInterface() {
      return Optional.ofNullable(normInterface);
    }

    private static Set<Target> declarationTargets() {
      return Set.of(PACKAGE, TYPE, FIELD, CONSTRUCTOR, METHOD, PARAMETER, LOCAL_VARIABLE);
    }
  }

  public enum Retention {
    SOURCE("SourceRetention"),
    CLASS("BinaryRetention"),
    RUNTIME("RuntimeRetention");

    private final String normInterface;

    Retention(String normInterface) {
      this.normInterface = normInterface;
    }

    public String normInterface() {
      return normInterface;
    }
  }
}
