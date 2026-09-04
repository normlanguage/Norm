package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

final class CoreAnnotationProtocolSupport {
  private CoreAnnotationProtocolSupport() {}

  static Optional<Protocol> locate(
      CoreProgram program, String name, Predicate<CoreNominalTypeKey> matches) {
    Protocol protocol = null;
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Interface declaration)
          || !matches.test(declaration.nominalType())) continue;
      if (protocol != null) throw new IllegalArgumentException(name + " protocol must be unique");
      protocol = new Protocol(record.id(), declaration);
    }
    return Optional.ofNullable(protocol);
  }

  static Optional<TypedInterceptor> typedInterceptor(
      CoreProgram program,
      String name,
      Predicate<CoreNominalTypeKey> matches,
      String parent,
      String contextIdentity) {
    Optional<Protocol> found = locate(program, name, matches);
    if (found.isEmpty()) return Optional.empty();
    Protocol protocol = found.orElseThrow();
    CoreDefinition.Interface declaration = protocol.declaration();
    if (declaration.typeParameters().size() != 1
        || declaration.typeParameters().getFirst().upperBound().isPresent()
        || declaration.typeParameters().getFirst().defaultType().isPresent()) {
      throw new IllegalArgumentException(name + " must declare one unbounded type parameter");
    }
    requireParent(program, name, protocol, parent);
    Map<String, DefinitionId> methods = methods(program, name, protocol);
    if (methods.size() != 2) {
      throw new IllegalArgumentException(name + " must declare two lifecycle methods");
    }
    DefinitionId before = require(methods, name, AnnotationAbi.BEFORE);
    DefinitionId after = require(methods, name, AnnotationAbi.AFTER);
    verifyTypedBefore(program, name, contextIdentity, protocol, before);
    verifyTypedAfter(program, name, contextIdentity, protocol, after);
    return Optional.of(new TypedInterceptor(protocol.id(), before, after));
  }

  private static void verifyTypedBefore(
      CoreProgram program,
      String name,
      String contextIdentity,
      Protocol protocol,
      DefinitionId id) {
    CoreDefinition.InterfaceMethod method = method(program, name, id);
    CoreType value = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    if (!receiver(program, protocol, id, method, List.of(value))
        || !method.typeParameters().isEmpty()
        || !method.returnType().equals(value)
        || method.parameterTypes().size() != 2
        || !builtin(method.parameterTypes().get(0), contextIdentity, List.of())
        || !method.parameterTypes().get(1).equals(value)) {
      throw new IllegalArgumentException(name + ".before has an invalid ABI");
    }
  }

  private static void verifyTypedAfter(
      CoreProgram program,
      String name,
      String contextIdentity,
      Protocol protocol,
      DefinitionId id) {
    CoreDefinition.InterfaceMethod method = method(program, name, id);
    CoreType value = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    if (!receiver(program, protocol, id, method, List.of(value))
        || !method.typeParameters().isEmpty()
        || !method.returnType().equals(CoreType.VOID)
        || method.parameterTypes().size() != 2
        || !builtin(method.parameterTypes().get(0), contextIdentity, List.of())
        || !builtin(method.parameterTypes().get(1), "std.core.FunctionCompletion", List.of())) {
      throw new IllegalArgumentException(name + ".after has an invalid ABI");
    }
  }

  static void requireParent(
      CoreProgram program, String name, Protocol protocol, String parentName) {
    if (protocol.declaration().directParents().size() != 1) {
      throw new IllegalArgumentException(name + " must extend " + parentName);
    }
    CoreType parent =
        CoreTypes.absolute(
            protocol.declaration().directParents().getFirst(), protocol.id(), program);
    if (!(parent instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference reference)
        || !(program.definition(program.resolve(protocol.id(), reference)).orElseThrow()
            instanceof CoreDefinition.Interface parentDeclaration)
        || !parentDeclaration.nominalType().module().name().equals(AnnotationAbi.MODULE)
        || !parentDeclaration.nominalType().packageName().equals(AnnotationAbi.PACKAGE)
        || !parentDeclaration.nominalType().name().equals(parentName)
        || !declared.arguments().isEmpty()) {
      throw new IllegalArgumentException(name + " must extend " + parentName);
    }
  }

  static Map<String, DefinitionId> methods(CoreProgram program, String name, Protocol protocol) {
    Map<String, DefinitionId> methods = new LinkedHashMap<>();
    for (CoreDefinitionLink link : protocol.declaration().declaredMethods()) {
      DefinitionId methodId = resolve(program, name, protocol.id(), link);
      CoreDefinition.InterfaceMethod method = method(program, name, methodId);
      if (methods.putIfAbsent(method.name(), methodId) != null) {
        throw new IllegalArgumentException(name + " lifecycle methods must be unique");
      }
    }
    return Map.copyOf(methods);
  }

  static DefinitionId require(Map<String, DefinitionId> methods, String protocol, String method) {
    DefinitionId id = methods.get(method);
    if (id == null) {
      throw new IllegalArgumentException(protocol + " lifecycle method is absent: " + method);
    }
    return id;
  }

  static CoreDefinition.InterfaceMethod method(CoreProgram program, String name, DefinitionId id) {
    if (!(program.definition(id).orElseThrow() instanceof CoreDefinition.InterfaceMethod method)) {
      throw new IllegalArgumentException(name + " member must be an interface method");
    }
    return method;
  }

  static boolean receiver(
      CoreProgram program,
      Protocol protocol,
      DefinitionId methodId,
      CoreDefinition.InterfaceMethod method,
      List<CoreType> arguments) {
    CoreType type = CoreTypes.absolute(method.receiverInterfaceType(), methodId, program);
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user
        && user.definition() instanceof DefinitionReference reference
        && program.resolve(methodId, reference).equals(protocol.id())
        && declared.arguments().equals(arguments)
        && declared.category() == CoreValueCategory.POLYMORPHIC
        && declared.nullability() == CoreNullability.NON_NULL;
  }

  static boolean builtin(CoreType type, String identity, List<CoreType> arguments) {
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals(identity)
        && declared.arguments().equals(arguments)
        && declared.category() == CoreValueCategory.IDENTITY
        && declared.nullability() == CoreNullability.NON_NULL;
  }

  private static DefinitionId resolve(
      CoreProgram program, String name, DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException(name + " contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  record Protocol(DefinitionId id, CoreDefinition.Interface declaration) {}

  record TypedInterceptor(DefinitionId interfaceId, DefinitionId before, DefinitionId after) {}
}
