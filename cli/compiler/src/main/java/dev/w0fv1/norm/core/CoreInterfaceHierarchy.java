package dev.w0fv1.norm.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreInterfaceHierarchy {
  private final CoreProgram program;

  CoreInterfaceHierarchy(CoreProgram program) {
    this.program = java.util.Objects.requireNonNull(program, "program");
  }

  public static List<CoreType> arguments(
      CoreProgram program, DefinitionId owner, CoreType root, DefinitionId target) {
    CoreType.Declared instance =
        new CoreInterfaceHierarchy(program).instances(owner, root).get(target);
    if (instance == null) {
      throw new IllegalArgumentException("target interface is absent from the interface hierarchy");
    }
    return instance.arguments();
  }

  Map<DefinitionId, CoreType.Declared> instances(DefinitionId owner, CoreType root) {
    Map<DefinitionId, CoreType.Declared> result = new LinkedHashMap<>();
    collect(instance(owner, root), result);
    return Map.copyOf(result);
  }

  void collect(DefinitionId owner, CoreType root, Map<DefinitionId, CoreType.Declared> result) {
    collect(instance(owner, root), result);
  }

  private void collect(Instance instance, Map<DefinitionId, CoreType.Declared> result) {
    CoreType.Declared previous = result.putIfAbsent(instance.definition(), instance.type());
    if (previous != null) {
      if (!previous.equals(instance.type())) {
        throw new IllegalArgumentException("interface inheritance has conflicting instantiations");
      }
      return;
    }
    for (CoreType parent : instance.declaration().directParents()) {
      CoreType instantiated =
          CoreTypes.absolute(parent, instance.definition(), program)
              .substitute(instance.type().arguments()::get);
      collect(instance(instance.definition(), instantiated), result);
    }
  }

  Instance instance(DefinitionId owner, CoreType type) {
    CoreType actual = nonNullable(CoreTypes.absolute(type, owner, program));
    if (!(actual instanceof CoreType.Declared declared)
        || declared.category() != CoreValueCategory.POLYMORPHIC
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("core type is not an interface");
    }
    DefinitionId definition = program.resolve(owner, reference);
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Interface declaration)
        || declared.arguments().size() != declaration.typeParameters().size()) {
      throw new IllegalArgumentException("core interface type does not match its nominal ABI");
    }
    return new Instance(definition, declaration, declared);
  }

  private static CoreType nonNullable(CoreType type) {
    if (!(type instanceof CoreType.Declared declared)
        || declared.nullability() == CoreNullability.NON_NULL) {
      return type;
    }
    return new CoreType.Declared(
        declared.constructor(),
        declared.arguments(),
        declared.category(),
        CoreNullability.NON_NULL);
  }

  record Instance(
      DefinitionId definition, CoreDefinition.Interface declaration, CoreType.Declared type) {}
}
