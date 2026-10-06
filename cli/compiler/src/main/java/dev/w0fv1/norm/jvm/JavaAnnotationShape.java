package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.*;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.Type;

final class JavaAnnotationShape {
  private JavaAnnotationShape() {}

  static boolean representable(
      CoreProgram program,
      CoreBinding binding,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    if (!(binding.shape() instanceof CoreBindingShape.Aggregate shape)) return false;
    return shape.typeParameters().isEmpty()
        && shape.fields().stream()
            .allMatch(
                field ->
                    elementType(program, binding.definition(), field.type(), javaTypes)
                        .isPresent());
  }

  static Optional<String> elementType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes) {
    return elementType(program, owner, type, javaTypes, new HashSet<>());
  }

  private static Optional<String> elementType(
      CoreProgram program,
      DefinitionId owner,
      CoreType type,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<DefinitionId> visited) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    if (absolute.isNullable() || !(absolute instanceof CoreType.Declared declared))
      return Optional.empty();
    if (declared.constructor() instanceof CoreTypeConstructor.Builtin builtin) {
      String identity = builtin.id().value();
      if (identity.equals("std.core.Class")) return Optional.of("java.lang.Class<?>");
      if (identity.equals("std.core.List") || identity.equals("std.core.Array")) {
        if (declared.arguments().size() != 1) return Optional.empty();
        return elementType(program, owner, declared.arguments().getFirst(), javaTypes, visited)
            .filter(element -> !element.endsWith("[]"))
            .map(element -> element + "[]");
      }
      if (identity.equals("std.core.CodePoint")) return Optional.of("int");
      String descriptor =
          JavaPlatformTypes.classDescriptors().get(new JarBindingClassReference.Builtin(identity));
      if (descriptor == null
          || descriptor.equals("V")
          || descriptor.startsWith("L") && !descriptor.equals("Ljava/lang/String;"))
        return Optional.empty();
      return Optional.of(Type.getType(descriptor).getClassName());
    }
    if (!(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference.External reference))
      return Optional.empty();
    var declaration = program.definition(reference.definition()).orElseThrow();
    CoreNominalTypeKey nominal;
    if (declaration instanceof CoreDefinition.Enum enumeration) {
      if (!declared.arguments().isEmpty()
          || enumeration.variants().stream().anyMatch(variant -> !variant.fields().isEmpty()))
        return Optional.empty();
      nominal = enumeration.nominalType();
    } else if (declaration instanceof CoreDefinition.Aggregate annotation
        && annotation.kind() == CoreAggregateKind.ANNOTATION) {
      if (!declared.arguments().isEmpty() || !annotation.typeParameters().isEmpty())
        return Optional.empty();
      if (!visited.add(reference.definition())) return Optional.empty();
      boolean representable =
          annotation.fields().stream()
              .allMatch(
                  field ->
                      elementType(program, reference.definition(), field.type(), javaTypes, visited)
                          .isPresent());
      visited.remove(reference.definition());
      if (!representable) return Optional.empty();
      nominal = annotation.nominalType();
    } else return Optional.empty();
    return Optional.of(
        javaTypes.getOrDefault(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name()),
            JavaApplicationTypeName.binaryName(nominal)));
  }
}
