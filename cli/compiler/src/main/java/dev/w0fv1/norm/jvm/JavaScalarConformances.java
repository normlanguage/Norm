package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.semantic.BuiltinTypeConformance;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class JavaScalarConformances {
  private JavaScalarConformances() {}

  public static List<String> canonicalTypes() {
    return JavaPlatformTypes.classDescriptors().values().stream()
        .map(
            descriptor ->
                descriptor.startsWith("L")
                    ? descriptor.substring(1, descriptor.length() - 1).replace('/', '.')
                    : JavaBoxedType.binaryNameForDescriptor(descriptor))
        .sorted()
        .toList();
  }

  public static List<BuiltinTypeConformance> derive(
      JarApiSchema schema, Map<String, JarBindingClassReference.Nominal> owners) {
    var types =
        schema.allTypes().stream().collect(Collectors.toMap(JavaApiType::binaryName, type -> type));
    var projector = JavaTypeProjector.forSchema(types);
    var result = new ArrayList<BuiltinTypeConformance>();
    JavaPlatformTypes.classDescriptors()
        .forEach(
            (reference, descriptor) -> {
              String binaryName =
                  descriptor.startsWith("L")
                      ? descriptor.substring(1, descriptor.length() - 1).replace('/', '.')
                      : JavaBoxedType.binaryNameForDescriptor(descriptor);
              var declaration = types.get(binaryName);
              if (declaration == null) return;
              String identity = ((JarBindingClassReference.Builtin) reference).typeId();
              var concrete = scalarType(identity);
              declaration
                  .signature()
                  .interfaces()
                  .forEach(
                      signature -> {
                        var projected =
                            projector
                                .project(signature, Map.of(), JavaTypeProjector.Position.VALUE)
                                .orElseThrow(
                                    () ->
                                        new IllegalArgumentException(
                                            "cannot project scalar Java interface " + signature));
                        result.add(
                            new BuiltinTypeConformance(concrete, semanticType(projected, owners)));
                      });
            });
    return result.stream()
        .distinct()
        .sorted(
            java.util.Comparator.comparing(
                    (BuiltinTypeConformance value) -> value.concreteType().identity())
                .thenComparing(value -> value.interfaceType().identity()))
        .toList();
  }

  private static SemanticType scalarType(String identity) {
    return SemanticType.declared(
        identity,
        identity.substring(identity.lastIndexOf('.') + 1),
        List.of(),
        identity.equals(SemanticType.ANY.identity())
                || identity.equals(SemanticType.NUMBER.identity())
            ? ValueCategory.POLYMORPHIC
            : ValueCategory.VALUE);
  }

  private static SemanticType semanticType(
      JavaBindingType type, Map<String, JarBindingClassReference.Nominal> owners) {
    if (type instanceof JavaReferenceType reference) {
      var scalar =
          JavaPlatformTypes.classDescriptors().entrySet().stream()
              .filter(entry -> entry.getValue().equals(reference.descriptor()))
              .map(entry -> ((JarBindingClassReference.Builtin) entry.getKey()).typeId())
              .findFirst();
      if (scalar.isPresent()) {
        String identity = scalar.orElseThrow();
        return scalarType(identity);
      }
      var owner = owners.get(reference.binaryName());
      if (owner == null)
        throw new IllegalArgumentException(
            "scalar Java interface has no owner: " + reference.binaryName());
      return SemanticType.declared(
          owner.packageName() + "." + owner.name(),
          owner.name(),
          reference.arguments().stream()
              .map(
                  argument ->
                      argument.variance() == JavaTypeVariance.UNBOUNDED
                          ? SemanticType.EXISTENTIAL
                          : semanticType(argument.type().orElseThrow(), owners))
              .toList(),
          ValueCategory.POLYMORPHIC);
    }
    if (type instanceof JavaBoxedType boxed) {
      String identity =
          JavaPlatformTypes.classDescriptors().entrySet().stream()
              .filter(entry -> entry.getValue().equals(boxed.primitive().descriptor()))
              .map(entry -> ((JarBindingClassReference.Builtin) entry.getKey()).typeId())
              .findFirst()
              .orElseThrow();
      return scalarType(identity);
    }
    throw new IllegalArgumentException("unsupported scalar Java interface argument: " + type);
  }
}
