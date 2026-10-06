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
    return program.definition(binding.definition()).orElseThrow()
            instanceof CoreDefinition.Aggregate annotation
        && representable(program, binding.definition(), annotation, javaTypes, new HashSet<>());
  }

  private static boolean representable(
      CoreProgram program,
      DefinitionId owner,
      CoreDefinition.Aggregate annotation,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<DefinitionId> visited) {
    if (annotation.kind() != CoreAggregateKind.ANNOTATION
        || !annotation.typeParameters().isEmpty()
        || annotation.constructors().size() != 1
        || !visited.add(owner)) return false;
    try {
      if (!(annotation.constructors().getFirst() instanceof DefinitionReference reference)
          || !(program.definition(program.resolve(owner, reference)).orElseThrow()
              instanceof CoreDefinition.Callable constructor)
          || constructor.parameters().size() != annotation.fields().size()) return false;
      for (int index = 0; index < annotation.fields().size(); index++) {
        var field = annotation.fields().get(index);
        var parameter = constructor.parameters().get(index);
        if (field.visibility() != CoreVisibility.PUBLIC
            || !field.name().equals(parameter.name())
            || !CoreTypes.absolute(field.type(), owner, program)
                .equals(
                    CoreTypes.absolute(
                        parameter.type(), program.resolve(owner, reference), program))
            || elementType(program, owner, field.type(), javaTypes, visited).isEmpty())
          return false;
      }
      return true;
    } finally {
      visited.remove(owner);
    }
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
      if (!declared.arguments().isEmpty()
          || !representable(program, reference.definition(), annotation, javaTypes, visited))
        return Optional.empty();
      nominal = annotation.nominalType();
    } else return Optional.empty();
    return Optional.of(
        javaTypes.getOrDefault(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name()),
            JavaApplicationTypeName.binaryName(nominal)));
  }
}
