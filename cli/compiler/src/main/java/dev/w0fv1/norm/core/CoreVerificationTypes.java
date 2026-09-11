package dev.w0fv1.norm.core;

import dev.w0fv1.norm.abi.BuiltinContracts;
import dev.w0fv1.norm.abi.ExceptionAbi;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class CoreVerificationTypes {
  private final CoreProgram program;
  private final CoreTypeRelations typeRelations;
  private final BuiltinContracts builtins = BuiltinContracts.standard();
  private final CoreInterfaceHierarchy interfaces;
  private final DefinitionId resolvedExceptionDefinition;

  CoreVerificationTypes(CoreProgram program) {
    this.program = Objects.requireNonNull(program, "program");
    typeRelations = new CoreTypeRelations(program.definitions());
    interfaces = new CoreInterfaceHierarchy(program);
    resolvedExceptionDefinition = indexExceptionDefinition();
  }

  CallableSignature callableSignature(DefinitionId id, String subject) {
    CoreDefinition definition = program.definition(id).orElseThrow();
    if (definition instanceof CoreDefinition.MethodSignature signature) {
      return new CallableSignature(
          Optional.of(signature.receiverType()),
          signature.typeParameters(),
          signature.parameterTypes(),
          signature.returnType(),
          List.of(),
          false,
          ((CoreType.Declared) signature.receiverType()).arguments().size()
              + signature.typeParameters().size());
    }
    if (definition instanceof CoreDefinition.Callable callable) {
      return new CallableSignature(
          callable.receiverType(),
          callable.typeParameters(),
          callable.parameterTypes(),
          callable.returnType(),
          callable.captureTypes(),
          true,
          callable.reifiedTypeLocals().size());
    }
    throw new IllegalArgumentException(subject + " is not callable");
  }

  record CallableSignature(
      Optional<CoreType> receiverType,
      List<CoreTypeParameter> typeParameters,
      List<CoreType> parameterTypes,
      CoreType returnType,
      List<CoreType> captureTypes,
      boolean implemented,
      int reifiedParameterCount) {
    boolean hasReceiver() {
      return receiverType.isPresent();
    }

    int receiverTypeParameterCount() {
      return receiverType.map(value -> ((CoreType.Declared) value).arguments().size()).orElse(0);
    }
  }

  boolean isAggregateConstructor(DefinitionId id) {
    for (CoreDefinitionRecord record : program.definitions()) {
      if (record.definition() instanceof CoreDefinition.Aggregate aggregate
          && aggregate.constructors().stream()
              .map(constructor -> resolve(record.id(), constructor))
              .anyMatch(id::equals)) {
        return true;
      }
    }
    return false;
  }

  DefinitionId methodOwner(DefinitionId id, CallableSignature method) {
    CoreType receiver = nonNullable(absolute(id, method.receiverType().orElseThrow()));
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("dispatch method receiver must be an aggregate type");
    }
    DefinitionId owner = resolveExternal(user.definition());
    if (!(program.definition(owner).orElseThrow() instanceof CoreDefinition.Aggregate)) {
      throw new IllegalArgumentException("dispatch method receiver must be an aggregate type");
    }
    return owner;
  }

  static CoreType.Declared aggregateType(DefinitionId id, CoreDefinition.Aggregate declaration) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(id)),
        java.util.stream.IntStream.range(0, declaration.typeParameters().size())
            .mapToObj(index -> new CoreType.Parameter(index, CoreNullability.NON_NULL))
            .map(CoreType.class::cast)
            .toList(),
        declaration.valueCategory(),
        CoreNullability.NON_NULL);
  }

  void collectInterfaceInstances(
      InterfaceInstance instance, Map<DefinitionId, CoreType.Declared> result) {
    interfaces.collect(instance.definition(), instance.type(), result);
  }

  Map<DefinitionId, CoreDefinition.MethodSignature> inheritedRequirements(
      DefinitionId interfaceId) {
    Map<DefinitionId, CoreDefinition.MethodSignature> result = new LinkedHashMap<>();
    collectRequirements(interfaceId, result, new HashSet<>());
    return Map.copyOf(result);
  }

  private void collectRequirements(
      DefinitionId interfaceId,
      Map<DefinitionId, CoreDefinition.MethodSignature> result,
      Set<DefinitionId> visited) {
    if (!visited.add(interfaceId)) return;
    CoreDefinition.Interface declaration =
        (CoreDefinition.Interface) program.definition(interfaceId).orElseThrow();
    for (CoreType parent : declaration.directParents()) {
      collectRequirements(interfaceInstance(interfaceId, parent).definition(), result, visited);
    }
    for (CoreDefinitionLink link : declaration.declaredMethods()) {
      DefinitionId methodId = resolve(interfaceId, link);
      result.put(
          methodId, (CoreDefinition.MethodSignature) program.definition(methodId).orElseThrow());
    }
  }

  void requireAcyclicInterface(DefinitionId root, DefinitionId current, Set<DefinitionId> visited) {
    if (current.equals(root)) {
      throw new IllegalArgumentException("interface inheritance must be acyclic");
    }
    if (!visited.add(current)) return;
    CoreDefinition.Interface declaration =
        (CoreDefinition.Interface) program.definition(current).orElseThrow();
    for (CoreType parent : declaration.directParents()) {
      requireAcyclicInterface(root, interfaceInstance(current, parent).definition(), visited);
    }
  }

  void requireAcyclicAggregate(DefinitionId root, DefinitionId current, Set<DefinitionId> visited) {
    if (current.equals(root)) {
      throw new IllegalArgumentException("aggregate inheritance must be acyclic");
    }
    if (!visited.add(current)) return;
    CoreDefinition definition = program.definition(current).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)
        || aggregate.parentType().isEmpty()) return;
    CoreType parent = nonNullable(absolute(current, aggregate.parentType().orElseThrow()));
    CoreTypeConstructor.User user =
        (CoreTypeConstructor.User) ((CoreType.Declared) parent).constructor();
    requireAcyclicAggregate(root, resolveExternal(user.definition()), visited);
  }

  boolean isReceiverOf(
      DefinitionId owner, CoreType type, DefinitionId nominal, int parameterCount) {
    CoreType actual = absolute(owner, type);
    if (actual.isNullable()
        || !(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(nominal)
        || declared.arguments().size() != parameterCount) {
      return false;
    }
    for (int index = 0; index < parameterCount; index++) {
      if (!(declared.arguments().get(index) instanceof CoreType.Parameter parameter)
          || parameter.index() != index
          || parameter.nullability() != CoreNullability.NON_NULL) {
        return false;
      }
    }
    return true;
  }

  InterfaceInstance interfaceInstance(DefinitionId owner, CoreType type) {
    CoreInterfaceHierarchy.Instance instance = interfaces.instance(owner, type);
    return new InterfaceInstance(instance.definition(), instance.declaration(), instance.type());
  }

  List<CoreType> interfaceSubstitutions(InterfaceInstance instance, DefinitionId target) {
    CoreType.Declared targetInstance =
        interfaces.instances(instance.definition(), instance.type()).get(target);
    return targetInstance == null ? null : targetInstance.arguments();
  }

  record InterfaceInstance(
      DefinitionId definition, CoreDefinition.Interface declaration, CoreType.Declared type) {}

  void requireExceptionType(DefinitionId owner, CoreType type, String subject) {
    CoreType value = absolute(owner, type);
    if (value.isNullable()
        || !(value instanceof CoreType.Declared declared)
        || declared.category() != CoreValueCategory.IDENTITY
        || !declared.arguments().isEmpty()
        || !(declared.constructor() instanceof CoreTypeConstructor.User)
        || aggregateView(value, exceptionDefinition()) == null) {
      throw new IllegalArgumentException(subject + " requires an Exception type");
    }
  }

  private DefinitionId exceptionDefinition() {
    if (resolvedExceptionDefinition == null) {
      throw new IllegalArgumentException("Exception root is absent");
    }
    return resolvedExceptionDefinition;
  }

  private DefinitionId indexExceptionDefinition() {
    DefinitionId result = null;
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)) continue;
      CoreNominalTypeKey nominal = aggregate.nominalType();
      if (!isExceptionRoot(nominal)) continue;
      verifyExceptionRoot(aggregate);
      if (result != null && !result.equals(record.id())) {
        throw new IllegalArgumentException("Exception root must be unique");
      }
      result = record.id();
    }
    return result;
  }

  private static boolean isExceptionRoot(CoreNominalTypeKey nominal) {
    return nominal.packageName().equals(ExceptionAbi.PACKAGE_NAME)
        && nominal.name().equals(ExceptionAbi.TYPE_NAME);
  }

  private static void verifyExceptionRoot(CoreDefinition.Aggregate declaration) {
    if (declaration.nominalType().visibility() != CoreVisibility.PUBLIC
        || declaration.valueCategory() != CoreValueCategory.IDENTITY
        || !declaration.typeParameters().isEmpty()
        || declaration.parentType().isPresent()
        || declaration.fieldCount() != 1
        || declaration.fields().size() != 1
        || declaration.fields().getFirst().ordinal() != ExceptionAbi.MESSAGE_FIELD_ORDINAL
        || !declaration.fields().getFirst().type().equals(CoreType.STRING)) {
      throw new IllegalArgumentException("Exception root ABI is invalid");
    }
  }

  void requireNonNullableReceiver(DefinitionId owner, CoreType receiver, String subject) {
    if (absolute(owner, receiver).isNullable()) {
      throw new IllegalArgumentException(subject + " requires a non-null receiver");
    }
  }

  List<CoreType> receiverArguments(
      DefinitionId targetId, CallableSignature target, CoreType.Declared receiver) {
    CoreType expected = absolute(targetId, target.receiverType().orElseThrow());
    if (expected instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user) {
      CoreType.Declared view = aggregateView(receiver, resolveExternal(user.definition()));
      if (view == null) {
        throw new IllegalArgumentException("method receiver does not implement the target owner");
      }
      return view.arguments();
    }
    return receiver.arguments();
  }

  CoreType effectiveClassReceiver(DefinitionId owner, CoreType receiver) {
    if (!(receiver instanceof CoreType.Parameter parameter)) return receiver;
    ParameterContext context = typeParameterContext(owner, parameter.index());
    if (context == null || context.parameter().upperBound().isEmpty()) return receiver;
    CoreType bound =
        nonNullable(absolute(context.owner(), context.parameter().upperBound().orElseThrow()));
    if (!(bound instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(program.definition(resolveExternal(user.definition())).orElseThrow()
            instanceof CoreDefinition.Aggregate)) {
      return receiver;
    }
    return bound;
  }

  CoreType instantiatedFieldType(
      DefinitionId owner, CoreType receiverType, CoreFieldReference reference) {
    DefinitionId targetId = resolve(owner, reference.owner());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    List<CoreField> fields =
        switch (targetDefinition) {
          case CoreDefinition.Aggregate aggregate -> aggregate.fields();
          default -> throw new IllegalArgumentException("field owner or ordinal is invalid");
        };
    CoreField field =
        fields.stream()
            .filter(candidate -> candidate.ordinal() == reference.ordinal())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("field owner or ordinal is invalid"));
    CoreType receiver = nonNullable(absolute(owner, receiverType));
    CoreDefinition.Aggregate target = (CoreDefinition.Aggregate) targetDefinition;
    CoreType.Declared declared = aggregateView(receiver, targetId);
    if (declared == null || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("field receiver does not match its owner");
    }
    return absolute(targetId, field.type()).substitute(declared.arguments()::get);
  }

  void verifyValueType(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "core value ABI");
  }

  void verifyReturnType(DefinitionId owner, CoreType type, int parameterCount) {
    if (type.equals(CoreType.VOID)) return;
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("core return ABI cannot contain a reference type");
    }
    verifyInhabitedType(owner, type, parameterCount, "core return ABI");
  }

  void verifyParameterType(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type) && !(type instanceof CoreType.Reference)) {
      throw new IllegalArgumentException("core parameter ABI cannot contain a nested reference");
    }
    verifyValueType(owner, type, parameterCount);
  }

  void verifyStoredType(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("stored core types cannot contain references");
    }
    verifyValueType(owner, type, parameterCount);
  }

  void verifyRuntimeTypeTemplate(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("runtime type templates cannot contain references");
    }
    verifyInhabitedType(owner, type, parameterCount, "runtime type template");
  }

  void verifyTypeParameters(
      DefinitionId owner, List<CoreTypeParameter> parameters, int parameterCount) {
    boolean defaultSeen = false;
    for (CoreTypeParameter parameter : parameters) {
      parameter
          .upperBound()
          .ifPresent(
              bound -> {
                verifyValueType(owner, bound, parameterCount);
                CoreType resolved = absolute(owner, bound);
                if (resolved.isNullable()) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class, interface, or type"
                          + " parameter");
                }
                if (nonNullable(resolved) instanceof CoreType.Parameter boundParameter) {
                  if (boundParameter.index() == parameter.index()) {
                    throw new IllegalArgumentException("cyclic type parameter bound");
                  }
                  return;
                }
                if (!(nonNullable(resolved) instanceof CoreType.Declared declared)
                    || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class, interface, or type"
                          + " parameter");
                }
                CoreDefinition definition =
                    program.definition(resolveExternal(user.definition())).orElseThrow();
                if (!(definition instanceof CoreDefinition.Interface)
                    && (!(definition instanceof CoreDefinition.Aggregate aggregate)
                        || aggregate.kind() != CoreAggregateKind.CLASS)) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class or interface");
                }
              });
      if (parameter.defaultType().isEmpty()) {
        if (defaultSeen) {
          throw new IllegalArgumentException(
              "required type parameter follows a default type parameter");
        }
        continue;
      }
      defaultSeen = true;
      CoreType defaultType = parameter.defaultType().orElseThrow();
      verifyValueType(owner, defaultType, parameterCount);
      Set<Integer> referenced = new HashSet<>();
      collectTypeParameters(defaultType, referenced);
      if (referenced.stream().anyMatch(index -> index >= parameter.index())) {
        throw new IllegalArgumentException(
            "type parameter default may reference earlier type parameters only");
      }
      if (parameter.upperBound().isPresent()) {
        CoreType bound = absolute(owner, parameter.upperBound().orElseThrow());
        CoreType actual = absolute(owner, defaultType);
        if (!isAssignableThroughTypeParameterBounds(bound, owner, actual, new HashSet<>())) {
          throw new IllegalArgumentException("type parameter default does not satisfy its bound");
        }
      }
    }
  }

  void verifyTypeArgumentBounds(
      DefinitionId owner,
      List<CoreTypeParameter> parameters,
      List<CoreType> substitutions,
      DefinitionId actualOwner) {
    List<CoreType> absoluteSubstitutions =
        substitutions.stream().map(type -> absolute(actualOwner, type)).toList();
    for (CoreTypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      CoreType expected =
          absolute(owner, parameter.upperBound().orElseThrow())
              .substitute(absoluteSubstitutions::get);
      CoreType actual = absoluteSubstitutions.get(parameter.index());
      if (isAssignable(expected, actual)) continue;
      if (actual instanceof CoreType.Parameter actualParameter) {
        ParameterContext context = typeParameterContext(actualOwner, actualParameter.index());
        if (context != null && context.parameter().upperBound().isPresent()) {
          CoreType actualBound =
              absolute(context.owner(), context.parameter().upperBound().orElseThrow());
          if (isAssignable(expected, actualBound)) continue;
        }
      }
      requireAssignable(expected, actual, "type argument bound");
    }
  }

  private ParameterContext typeParameterContext(DefinitionId owner, int index) {
    CoreDefinition definition = program.definition(owner).orElseThrow();
    if (definition instanceof CoreDefinition.Callable callable) {
      if (index < callable.receiverTypeParameterCount()) {
        return receiverTypeParameterContext(owner, callable.receiverType().orElseThrow(), index);
      }
      return callable.typeParameters().stream()
          .filter(parameter -> parameter.index() == index)
          .findFirst()
          .map(parameter -> new ParameterContext(owner, parameter))
          .orElse(null);
    }
    if (definition instanceof CoreDefinition.MethodSignature method) {
      CoreType receiver = absolute(owner, method.receiverType());
      int receiverCount =
          receiver instanceof CoreType.Declared declared ? declared.arguments().size() : 0;
      if (index < receiverCount) {
        return receiverTypeParameterContext(owner, method.receiverType(), index);
      }
      return method.typeParameters().stream()
          .filter(parameter -> parameter.index() == index)
          .findFirst()
          .map(parameter -> new ParameterContext(owner, parameter))
          .orElse(null);
    }
    List<CoreTypeParameter> parameters =
        switch (definition) {
          case CoreDefinition.Aggregate aggregate -> aggregate.typeParameters();
          case CoreDefinition.Interface interfaceDefinition -> interfaceDefinition.typeParameters();
          case CoreDefinition.Enum enumDefinition -> enumDefinition.typeParameters();
          case CoreDefinition.BuiltinConformance conformance -> conformance.typeParameters();
          case CoreDefinition.Callable ignored -> throw new IllegalStateException();
          case CoreDefinition.MethodSignature ignored -> throw new IllegalStateException();
        };
    return parameters.stream()
        .filter(parameter -> parameter.index() == index)
        .findFirst()
        .map(parameter -> new ParameterContext(owner, parameter))
        .orElse(null);
  }

  private ParameterContext receiverTypeParameterContext(
      DefinitionId owner, CoreType receiverType, int index) {
    CoreType receiver = absolute(owner, receiverType);
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      return null;
    }
    DefinitionId receiverOwner = resolveExternal(user.definition());
    CoreDefinition definition = program.definition(receiverOwner).orElseThrow();
    List<CoreTypeParameter> parameters =
        definition instanceof CoreDefinition.Aggregate aggregate
            ? aggregate.typeParameters()
            : definition instanceof CoreDefinition.Interface interfaceDefinition
                ? interfaceDefinition.typeParameters()
                : List.of();
    return parameters.stream()
        .filter(parameter -> parameter.index() == index)
        .findFirst()
        .map(parameter -> new ParameterContext(receiverOwner, parameter))
        .orElse(null);
  }

  record ParameterContext(DefinitionId owner, CoreTypeParameter parameter) {}

  void verifyLocalType(DefinitionId owner, CoreLocal local, int parameterCount) {
    if (local.kind() == CoreLocal.Kind.REIFIED_TYPE || local.kind() == CoreLocal.Kind.ITERATOR) {
      if (!local.type().equals(CoreType.DYNAMIC)) {
        throw new IllegalArgumentException("internal runtime locals require dynamic type");
      }
      return;
    }
    if (CoreTypes.containsReference(local.type())
        && !(local.type() instanceof CoreType.Reference)) {
      throw new IllegalArgumentException("core local ABI cannot contain a nested reference");
    }
    verifyValueType(owner, local.type(), parameterCount);
  }

  private void verifyInhabitedType(
      DefinitionId owner, CoreType type, int parameterCount, String subject) {
    switch (type) {
      case CoreType.Parameter parameter -> {
        if (parameter.index() >= parameterCount) {
          throw new IllegalArgumentException("core type parameter is outside its ABI");
        }
      }
      case CoreType.Declared declared -> {
        if (declared.arguments().stream().anyMatch(CoreTypes::containsReference)) {
          throw new IllegalArgumentException("declared core types cannot contain references");
        }
        boolean classLiteral =
            declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
                && builtin.id().value().equals("std.core.Class");
        declared.arguments().stream()
            .filter(argument -> !argument.equals(CoreType.EXISTENTIAL))
            .filter(argument -> !classLiteral || !argument.equals(CoreType.VOID))
            .forEach(argument -> verifyInhabitedType(owner, argument, parameterCount, subject));
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin -> verifyBuiltinType(builtin, declared);
          case CoreTypeConstructor.User user -> verifyUserType(owner, user, declared);
        }
      }
      case CoreType.Function function -> {
        if (function.returnType().equals(CoreType.EXISTENTIAL)
            && function.parameterTypes().isEmpty()) {
          break;
        }
        if (CoreTypes.containsReference(function.returnType())
            || function.parameterTypes().stream().anyMatch(CoreTypes::containsReference)) {
          throw new IllegalArgumentException("function core types cannot contain references");
        }
        verifyReturnType(owner, function.returnType(), parameterCount);
        function
            .parameterTypes()
            .forEach(parameter -> verifyValueType(owner, parameter, parameterCount));
      }
      case CoreType.Reference reference -> {
        verifyValueType(owner, reference.target(), parameterCount);
        CoreType target = absolute(owner, reference.target());
        if (!(target instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.VALUE) {
          throw new IllegalArgumentException("reference target must be a value type");
        }
      }
      case CoreType.Special ignored ->
          throw new IllegalArgumentException(subject + " requires an inhabitable type");
    }
  }

  private void verifyBuiltinType(
      CoreTypeConstructor.Builtin constructor, CoreType.Declared declared) {
    String identity = constructor.id().value();
    String prefix = "std.core.";
    if (!identity.startsWith(prefix)) {
      throw new IllegalArgumentException("unknown builtin core type " + identity);
    }
    var definition =
        builtins
            .type(identity.substring(prefix.length()))
            .orElseThrow(
                () -> new IllegalArgumentException("unknown builtin core type " + identity));
    if (definition.arity() != declared.arguments().size()) {
      throw new IllegalArgumentException("builtin core type has the wrong arity");
    }
    CoreValueCategory expected =
        switch (definition.symbol().type().category()) {
          case IDENTITY -> CoreValueCategory.IDENTITY;
          case VALUE -> CoreValueCategory.VALUE;
          case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
          case VOID, DYNAMIC ->
              throw new IllegalArgumentException("builtin core type cannot be inhabited");
        };
    if (declared.category() != expected) {
      throw new IllegalArgumentException("builtin core type has the wrong value category");
    }
  }

  private void verifyUserType(
      DefinitionId owner, CoreTypeConstructor.User constructor, CoreType.Declared declared) {
    DefinitionId targetId = resolve(owner, constructor.definition());
    CoreDefinition target = program.definition(targetId).orElseThrow();
    int arity;
    CoreValueCategory category;
    if (target instanceof CoreDefinition.Aggregate declaration) {
      arity = declaration.typeParameters().size();
      category = declaration.valueCategory();
    } else if (target instanceof CoreDefinition.Enum declaration) {
      arity = declaration.typeParameters().size();
      category = CoreValueCategory.VALUE;
    } else if (target instanceof CoreDefinition.Interface declaration) {
      arity = declaration.typeParameters().size();
      category = CoreValueCategory.POLYMORPHIC;
    } else {
      throw new IllegalArgumentException("declared core type target is not nominal");
    }
    if (declared.arguments().size() != arity || declared.category() != category) {
      throw new IllegalArgumentException(
          "declared core type does not match its nominal ABI: target="
              + targetId
              + ", expectedArity="
              + arity
              + ", actualArity="
              + declared.arguments().size()
              + ", expectedCategory="
              + category
              + ", actualCategory="
              + declared.category());
    }
    List<CoreTypeParameter> parameters =
        switch (target) {
          case CoreDefinition.Aggregate declaration -> declaration.typeParameters();
          case CoreDefinition.Enum declaration -> declaration.typeParameters();
          case CoreDefinition.Interface declaration -> declaration.typeParameters();
          default -> throw new IllegalStateException("nominal definition kind changed");
        };
    verifyTypeArgumentBounds(targetId, parameters, declared.arguments(), owner);
  }

  private CoreType declaredArgument(
      DefinitionId owner, CoreType type, String builtinIdentity, int index) {
    CoreType absolute = nonNullable(absolute(owner, type));
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(builtinIdentity)
        || index >= declared.arguments().size()) {
      throw new IllegalArgumentException("core expression has an incompatible declared type");
    }
    return declared.arguments().get(index);
  }

  static void collectTypeParameters(CoreType type, Set<Integer> result) {
    switch (type) {
      case CoreType.Parameter parameter -> result.add(parameter.index());
      case CoreType.Declared declared ->
          declared.arguments().forEach(argument -> collectTypeParameters(argument, result));
      case CoreType.Function function -> {
        collectTypeParameters(function.returnType(), result);
        function.parameterTypes().forEach(argument -> collectTypeParameters(argument, result));
      }
      case CoreType.Reference reference -> collectTypeParameters(reference.target(), result);
      case CoreType.Special ignored -> {}
    }
  }

  static void verifyDenseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (!denseArguments(arguments, parameterCount)) {
      throw new IllegalArgumentException("core arguments do not match the target arity");
    }
  }

  static boolean denseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (arguments.size() != parameterCount) return false;
    boolean[] supplied = new boolean[parameterCount];
    for (CoreArgument argument : arguments) {
      if (argument.parameterIndex() >= parameterCount || supplied[argument.parameterIndex()]) {
        return false;
      }
      supplied[argument.parameterIndex()] = true;
    }
    return true;
  }

  static CoreLocal local(CoreDefinition.Callable callable, int index) {
    if (index < 0 || index >= callable.locals().size()) {
      throw new IllegalArgumentException("core local use is outside the local table");
    }
    return callable.locals().get(index);
  }

  DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("core program contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  static DefinitionId resolveExternal(CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference.External external)) {
      throw new IllegalArgumentException("absolute core type contains a relative reference");
    }
    return external.definition();
  }

  CoreType absolute(DefinitionId owner, CoreType type) {
    return CoreTypes.absolute(type, owner, program);
  }

  void requireAssignable(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    CoreType expectedType = absolute(expectedOwner, expected);
    CoreType actualType = absolute(actualOwner, actual);
    if (!isAssignableThroughTypeParameterBounds(
        expectedType, actualOwner, actualType, new HashSet<>())) {
      throw new IllegalArgumentException(
          subject
              + " type does not match its ABI: expected "
              + expectedType
              + ", actual "
              + actualType);
    }
  }

  private boolean isAssignableThroughTypeParameterBounds(
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      Set<Map.Entry<DefinitionId, Integer>> visiting) {
    if (isAssignable(expected, actual)) return true;
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (!(nonNullable(actual) instanceof CoreType.Parameter parameter)) return false;
    Map.Entry<DefinitionId, Integer> key = Map.entry(actualOwner, parameter.index());
    if (!visiting.add(key)) return false;
    ParameterContext context = typeParameterContext(actualOwner, parameter.index());
    if (context == null || context.parameter().upperBound().isEmpty()) return false;
    CoreType bound = absolute(context.owner(), context.parameter().upperBound().orElseThrow());
    if (actual.isNullable()) bound = bound.asNullable();
    return isAssignableThroughTypeParameterBounds(expected, context.owner(), bound, visiting);
  }

  void requireAssignable(CoreType expected, CoreType actual, String subject) {
    if (!isAssignable(expected, actual)) {
      throw new IllegalArgumentException(
          subject + " type does not match its ABI: expected " + expected + ", actual " + actual);
    }
  }

  void requireSameType(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireSameAbsoluteType(
        absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  static void requireSameAbsoluteType(CoreType expected, CoreType actual, String subject) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  boolean isAssignable(CoreType expected, CoreType actual) {
    return typeRelations.isAssignable(expected, actual);
  }

  CoreType.Declared aggregateView(CoreType type, DefinitionId target) {
    return typeRelations.view(type, target);
  }

  static boolean isNumericLeaf(CoreType type) {
    CoreType value = nonNullable(type);
    return value.equals(CoreType.INTEGER)
        || value.equals(CoreType.LONG)
        || value.equals(CoreType.FLOAT)
        || value.equals(CoreType.DOUBLE);
  }

  CoreType realizedInterface(CoreType actual, DefinitionId target) {
    return typeRelations.view(actual, target);
  }

  static CoreType safeResult(CoreType result, boolean nullSafe, CoreType receiver) {
    return nullSafe && receiver.isNullable() ? result.asNullable() : result;
  }

  static boolean mayContainNull(CoreType type) {
    return type.equals(CoreType.NULL) || type.isNullable() || type instanceof CoreType.Parameter;
  }

  static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          declared.nullability() == CoreNullability.NON_NULL
              ? declared
              : new CoreType.Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NON_NULL);
      case CoreType.Function function ->
          function.nullability() == CoreNullability.NON_NULL
              ? function
              : new CoreType.Function(
                  function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          parameter.nullability() == CoreNullability.NON_NULL
              ? parameter
              : new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }

  void verifyExceptionDescendant(DefinitionId id, CoreDefinition.Aggregate declaration) {
    if (resolvedExceptionDefinition != null
        && !declaration.typeParameters().isEmpty()
        && aggregateView(aggregateType(id, declaration), resolvedExceptionDefinition) != null) {
      throw new IllegalArgumentException("Exception descendants cannot be generic");
    }
  }
}
