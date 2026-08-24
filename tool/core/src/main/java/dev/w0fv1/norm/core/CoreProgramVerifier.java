package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.PatternCoverage;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CoreProgramVerifier {
  private final CoreProgram program;
  private final BuiltinCatalog builtins = BuiltinCatalog.standard();
  private final Deque<Control> controls = new ArrayDeque<>();

  private CoreProgramVerifier(CoreProgram program) {
    this.program = Objects.requireNonNull(program, "program");
  }

  static void verify(CoreProgram program) {
    new CoreProgramVerifier(program).verify();
  }

  private void verify() {
    for (CoreDefinitionRecord record : program.definitions()) {
      if (record.definition() instanceof CoreDefinition.Interface declaration) {
        verifyInterface(record.id(), declaration);
      }
    }
    for (CoreDefinitionRecord record : program.definitions()) {
      switch (record.definition()) {
        case CoreDefinition.Callable callable -> verifyCallable(record.id(), callable);
        case CoreDefinition.Class declaration -> verifyClass(record.id(), declaration);
        case CoreDefinition.Enum declaration -> verifyEnum(record.id(), declaration);
        case CoreDefinition.Interface ignored -> {}
        case CoreDefinition.InterfaceMethod method -> verifyInterfaceMethod(record.id(), method);
        case CoreDefinition.BuiltinConformance conformance ->
            verifyBuiltinConformance(record.id(), conformance);
      }
    }
    verifyBuiltinDispatchUniqueness();
  }

  private void verifyBuiltinDispatchUniqueness() {
    Map<BuiltinTypeId, Set<DefinitionId>> indexed = new LinkedHashMap<>();
    Map<BuiltinTypeId, Map<DefinitionId, CoreType.Declared>> interfaces = new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance)) continue;
      CoreType.Declared concrete =
          (CoreType.Declared) absolute(record.id(), conformance.concreteBuiltinType());
      BuiltinTypeId builtin = ((CoreTypeConstructor.Builtin) concrete.constructor()).id();
      InterfaceInstance instance = interfaceInstance(record.id(), conformance.interfaceType());
      CoreType.Declared previous =
          interfaces
              .computeIfAbsent(builtin, ignored -> new LinkedHashMap<>())
              .putIfAbsent(instance.definition(), instance.type());
      if (previous != null && !previous.equals(instance.type())) {
        throw new IllegalArgumentException("builtin interface instantiations conflict");
      }
      Set<DefinitionId> requirements = indexed.computeIfAbsent(builtin, ignored -> new HashSet<>());
      for (CoreWitness witness : conformance.witnesses()) {
        if (!requirements.add(resolve(record.id(), witness.requirement()))) {
          throw new IllegalArgumentException("builtin interface dispatch must be unique");
        }
      }
    }
  }

  private void verifyCallable(DefinitionId id, CoreDefinition.Callable callable) {
    int parameterCount = callable.reifiedTypeLocals().size();
    verifyTypeParameters(id, callable.typeParameters(), parameterCount);
    callable.receiverType().ifPresent(type -> verifyValueType(id, type, parameterCount));
    callable.parameterTypes().forEach(type -> verifyValueType(id, type, parameterCount));
    verifyReturnType(id, callable.returnType(), parameterCount);
    callable.locals().forEach(local -> verifyLocalType(id, local, parameterCount));
    verifyBlock(id, callable, callable.body());
  }

  private void verifyClass(DefinitionId id, CoreDefinition.Class declaration) {
    declaration
        .fields()
        .forEach(field -> verifyValueType(id, field.type(), declaration.typeParameters().size()));
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    Set<DefinitionId> interfaces = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inheritedInterfaces = new LinkedHashMap<>();
    for (CoreConformance conformance : declaration.conformances()) {
      verifyValueType(id, conformance.interfaceType(), declaration.typeParameters().size());
      InterfaceInstance instance = interfaceInstance(id, conformance.interfaceType());
      if (!interfaces.add(instance.definition())) {
        throw new IllegalArgumentException("class conformances must be unique");
      }
      collectInterfaceInstances(instance, inheritedInterfaces);
      verifyConformance(id, declaration, instance, conformance);
    }
  }

  private void verifyInterface(DefinitionId id, CoreDefinition.Interface declaration) {
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    Set<DefinitionId> parents = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inherited = new LinkedHashMap<>();
    for (CoreType parent : declaration.directParents()) {
      verifyValueType(id, parent, declaration.typeParameters().size());
      InterfaceInstance instance = interfaceInstance(id, parent);
      if (!parents.add(instance.definition())) {
        throw new IllegalArgumentException("direct interface parents must be unique");
      }
      collectInterfaceInstances(instance, inherited);
      requireAcyclicInterface(id, instance.definition(), new HashSet<>());
    }
    Set<DefinitionId> methods = new HashSet<>();
    for (CoreDefinitionLink link : declaration.declaredMethods()) {
      DefinitionId methodId = resolve(id, link);
      if (!methods.add(methodId)) {
        throw new IllegalArgumentException("declared interface methods must be unique");
      }
      CoreDefinition target = program.definition(methodId).orElseThrow();
      if (!(target instanceof CoreDefinition.InterfaceMethod method)
          || !isReceiverOf(
              methodId, method.receiverInterfaceType(), id, declaration.typeParameters().size())) {
        throw new IllegalArgumentException("declared interface method has the wrong receiver ABI");
      }
    }
  }

  private void collectInterfaceInstances(
      InterfaceInstance instance, Map<DefinitionId, CoreType.Declared> result) {
    CoreType.Declared previous = result.putIfAbsent(instance.definition(), instance.type());
    if (previous != null) {
      if (!previous.equals(instance.type())) {
        throw new IllegalArgumentException("interface inheritance has conflicting instantiations");
      }
      return;
    }
    for (CoreType parent : instance.declaration().directParents()) {
      CoreType instantiated =
          absolute(instance.definition(), parent).substitute(instance.type().arguments()::get);
      collectInterfaceInstances(interfaceInstance(instance.definition(), instantiated), result);
    }
  }

  private void verifyInterfaceMethod(DefinitionId id, CoreDefinition.InterfaceMethod method) {
    InterfaceInstance receiver = interfaceInstance(id, method.receiverInterfaceType());
    int receiverParameterCount = receiver.declaration().typeParameters().size();
    int parameterCount = receiverParameterCount + method.typeParameters().size();
    if (!isReceiverOf(
        id, method.receiverInterfaceType(), receiver.definition(), receiverParameterCount)) {
      throw new IllegalArgumentException(
          "interface method receiver must expose its type parameters");
    }
    verifyTypeParameters(id, method.typeParameters(), parameterCount);
    method.parameterTypes().forEach(type -> verifyValueType(id, type, parameterCount));
    verifyReturnType(id, method.returnType(), parameterCount);
  }

  private void verifyConformance(
      DefinitionId classId,
      CoreDefinition.Class declaration,
      InterfaceInstance instance,
      CoreConformance conformance) {
    Map<DefinitionId, CoreDefinition.InterfaceMethod> requirements =
        inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirementId = resolve(classId, witness.requirement());
      if (witnesses.putIfAbsent(requirementId, witness.implementation()) != null) {
        throw new IllegalArgumentException("conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.InterfaceMethod> entry : requirements.entrySet()) {
      DefinitionId requirementId = entry.getKey();
      CoreDefinition.InterfaceMethod requirement = entry.getValue();
      CoreWitnessTarget witness = witnesses.get(requirementId);
      if (!(witness instanceof CoreWitnessTarget.Callable callableWitness)) {
        throw new IllegalArgumentException("source class witnesses must target callables");
      }
      DefinitionId implementationId = resolve(classId, callableWitness.definition());
      CoreDefinition implementationDefinition = program.definition(implementationId).orElseThrow();
      if (!(implementationDefinition instanceof CoreDefinition.Callable implementation)
          || implementation.receiverType().isEmpty()
          || implementation.reifiedTypeLocals().size()
              != declaration.typeParameters().size() + requirement.typeParameters().size()
          || !isReceiverOf(
              implementationId,
              implementation.receiverType().orElseThrow(),
              classId,
              declaration.typeParameters().size())) {
        throw new IllegalArgumentException(
            "conformance witness implementation is not a class method");
      }
      InterfaceInstance requirementOwner =
          interfaceInstance(requirementId, requirement.receiverInterfaceType());
      List<CoreType> substitutions =
          interfaceSubstitutions(instance, requirementOwner.definition());
      if (substitutions != null) {
        substitutions = new ArrayList<>(substitutions);
        for (int index = 0; index < requirement.typeParameters().size(); index++) {
          substitutions.add(
              new CoreType.Parameter(
                  declaration.typeParameters().size() + index, CoreNullability.NON_NULL));
        }
      }
      if (substitutions == null
          || implementation.parameterTypes().size() != requirement.parameterTypes().size()) {
        throw new IllegalArgumentException(
            "conformance witness ABI does not match its requirement");
      }
      for (int parameter = 0; parameter < requirement.parameterTypes().size(); parameter++) {
        CoreType expected =
            absolute(requirementId, requirement.parameterTypes().get(parameter))
                .substitute(substitutions::get);
        requireSameType(
            expected,
            absolute(implementationId, implementation.parameterTypes().get(parameter)),
            "conformance witness parameter");
      }
      CoreType expectedReturn =
          absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
      requireSameType(
          expectedReturn,
          absolute(implementationId, implementation.returnType()),
          "conformance witness return");
      for (int index = 0; index < requirement.typeParameters().size(); index++) {
        CoreTypeParameter requiredParameter = requirement.typeParameters().get(index);
        CoreTypeParameter implementationParameter = implementation.typeParameters().get(index);
        if (requiredParameter.upperBound().isPresent()
            != implementationParameter.upperBound().isPresent()) {
          throw new IllegalArgumentException("conformance witness generic bound does not match");
        }
        if (requiredParameter.upperBound().isPresent()) {
          CoreType expectedBound =
              absolute(requirementId, requiredParameter.upperBound().orElseThrow())
                  .substitute(substitutions::get);
          requireSameType(
              expectedBound,
              absolute(implementationId, implementationParameter.upperBound().orElseThrow()),
              "conformance witness generic bound");
        }
      }
    }
  }

  private void verifyBuiltinConformance(
      DefinitionId owner, CoreDefinition.BuiltinConformance conformance) {
    int parameterCount = conformance.typeParameters().size();
    verifyTypeParameters(owner, conformance.typeParameters(), parameterCount);
    verifyValueType(owner, conformance.concreteBuiltinType(), parameterCount);
    CoreType concrete = absolute(owner, conformance.concreteBuiltinType());
    if (!(concrete instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin)) {
      throw new IllegalArgumentException("builtin conformance requires a builtin concrete type");
    }
    verifyValueType(owner, conformance.interfaceType(), parameterCount);
    InterfaceInstance instance = interfaceInstance(owner, conformance.interfaceType());
    Map<DefinitionId, CoreDefinition.InterfaceMethod> requirements =
        inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirement = resolve(owner, witness.requirement());
      if (witnesses.putIfAbsent(requirement, witness.implementation()) != null) {
        throw new IllegalArgumentException("builtin conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("builtin conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.InterfaceMethod> entry : requirements.entrySet()) {
      CoreWitnessTarget witness = witnesses.get(entry.getKey());
      if (!(witness instanceof CoreWitnessTarget.Intrinsic intrinsic)
          || !matchesIntrinsicWitness(
              owner, concrete, instance, entry.getKey(), entry.getValue(), intrinsic.intrinsic())) {
        throw new IllegalArgumentException(
            "builtin conformance witness ABI does not match: "
                + concrete
                + " -> "
                + conformance.interfaceType()
                + " via "
                + witness);
      }
    }
  }

  private boolean matchesIntrinsicWitness(
      DefinitionId owner,
      CoreType concrete,
      InterfaceInstance instance,
      DefinitionId requirementId,
      CoreDefinition.InterfaceMethod requirement,
      dev.w0fv1.norm.builtin.IntrinsicId intrinsic) {
    if (!requirement.typeParameters().isEmpty()) return false;
    InterfaceInstance requirementOwner =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    List<CoreType> interfaceArguments =
        interfaceSubstitutions(instance, requirementOwner.definition());
    if (interfaceArguments == null) return false;
    for (BuiltinCatalog.IntrinsicCandidate candidate : builtins.intrinsicCandidates(intrinsic)) {
      if (candidate.receiver().isEmpty()
          || candidate.runtimeType()
          || candidate.parameters().size() != requirement.parameterTypes().size()) {
        continue;
      }
      Map<String, CoreType> substitutions = new LinkedHashMap<>();
      if (!bindPattern(concrete, candidate.receiver().orElseThrow(), substitutions)) continue;
      boolean parametersMatch = true;
      for (int index = 0; index < candidate.parameters().size(); index++) {
        CoreType expected =
            absolute(requirementId, requirement.parameterTypes().get(index))
                .substitute(interfaceArguments::get);
        if (!matchesSemanticType(
            expected, candidate.parameters().get(index).type(), substitutions)) {
          parametersMatch = false;
          break;
        }
      }
      CoreType expectedReturn =
          absolute(requirementId, requirement.returnType()).substitute(interfaceArguments::get);
      if (parametersMatch
          && matchesSemanticType(expectedReturn, candidate.result(), substitutions)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesSemanticType(
      CoreType expected, SemanticType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType substituted = substitutions.get(pattern.identity());
        yield substituted != null
            && expected.equals(pattern.isNullable() ? substituted.asNullable() : substituted);
      }
      case VOID -> expected.equals(CoreType.VOID);
      case NULL -> expected.equals(CoreType.NULL);
      case ERROR -> true;
      case DECLARED -> {
        if (!(expected instanceof CoreType.Declared declared)
            || declared.arguments().size() != pattern.arguments().size()
            || declared.category() != category(pattern.category())
            || declared.isNullable() != pattern.isNullable()
            || !matchesSemanticConstructor(declared.constructor(), pattern.identity())) {
          yield false;
        }
        boolean matches = true;
        for (int index = 0; index < pattern.arguments().size(); index++) {
          if (!matchesSemanticType(
              declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
            matches = false;
            break;
          }
        }
        yield matches;
      }
    };
  }

  private boolean matchesSemanticConstructor(
      CoreTypeConstructor constructor, String semanticIdentity) {
    return switch (constructor) {
      case CoreTypeConstructor.Builtin builtin -> builtin.id().value().equals(semanticIdentity);
      case CoreTypeConstructor.User user -> {
        DefinitionId id = resolveExternal(user.definition());
        CoreDefinition definition = program.definition(id).orElseThrow();
        CoreNominalTypeKey nominal =
            switch (definition) {
              case CoreDefinition.Class declaration -> declaration.nominalType();
              case CoreDefinition.Enum declaration -> declaration.nominalType();
              case CoreDefinition.Interface declaration -> declaration.nominalType();
              default -> null;
            };
        yield nominal != null
            && nominal.module().name().equals("std")
            && (nominal.packageName() + "." + nominal.name()).equals(semanticIdentity);
      }
    };
  }

  private Map<DefinitionId, CoreDefinition.InterfaceMethod> inheritedRequirements(
      DefinitionId interfaceId) {
    Map<DefinitionId, CoreDefinition.InterfaceMethod> result = new LinkedHashMap<>();
    collectRequirements(interfaceId, result, new HashSet<>());
    return Map.copyOf(result);
  }

  private void collectRequirements(
      DefinitionId interfaceId,
      Map<DefinitionId, CoreDefinition.InterfaceMethod> result,
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
          methodId, (CoreDefinition.InterfaceMethod) program.definition(methodId).orElseThrow());
    }
  }

  private void requireAcyclicInterface(
      DefinitionId root, DefinitionId current, Set<DefinitionId> visited) {
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

  private boolean isReceiverOf(
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

  private InterfaceInstance interfaceInstance(DefinitionId owner, CoreType type) {
    CoreType actual = nonNullable(absolute(owner, type));
    if (!(actual instanceof CoreType.Declared declared)
        || declared.category() != CoreValueCategory.POLYMORPHIC
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("core type is not an interface");
    }
    DefinitionId definition = resolveExternal(user.definition());
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Interface declaration)
        || declared.arguments().size() != declaration.typeParameters().size()) {
      throw new IllegalArgumentException("core interface type does not match its nominal ABI");
    }
    return new InterfaceInstance(definition, declaration, declared);
  }

  private List<CoreType> interfaceSubstitutions(InterfaceInstance instance, DefinitionId target) {
    if (instance.definition().equals(target)) return instance.type().arguments();
    for (CoreType parent : instance.declaration().directParents()) {
      CoreType instantiated =
          absolute(instance.definition(), parent).substitute(instance.type().arguments()::get);
      List<CoreType> result =
          interfaceSubstitutions(interfaceInstance(instance.definition(), instantiated), target);
      if (result != null) return result;
    }
    return null;
  }

  private record InterfaceInstance(
      DefinitionId definition, CoreDefinition.Interface declaration, CoreType.Declared type) {}

  private void verifyEnum(DefinitionId id, CoreDefinition.Enum declaration) {
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    if (declaration.variants().stream().map(CoreEnumVariant::key).distinct().count()
        != declaration.variants().size()) {
      throw new IllegalArgumentException("enum variants must be unique");
    }
    declaration
        .variants()
        .forEach(
            variant ->
                variant
                    .fields()
                    .forEach(
                        field ->
                            verifyValueType(
                                id, field.type(), declaration.typeParameters().size())));
  }

  private void verifyBlock(DefinitionId owner, CoreDefinition.Callable callable, CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      switch (statement) {
        case CoreStatement.LocalDeclaration local -> {
          CoreLocal target = local(callable, local.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("local declaration must bind a variable local");
          }
          verifyExpression(owner, callable, local.initializer());
          requireAssignable(
              owner, target.type(), owner, local.initializer().type(), "local initializer");
        }
        case CoreStatement.LocalAssignment assignment -> {
          CoreLocal target = local(callable, assignment.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE
              && target.kind() != CoreLocal.Kind.PARAMETER) {
            throw new IllegalArgumentException("local assignment target is not mutable storage");
          }
          verifyExpression(owner, callable, assignment.value());
          requireAssignable(
              owner, target.type(), owner, assignment.value().type(), "local assignment");
        }
        case CoreStatement.FieldAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          verifyExpression(owner, callable, assignment.value());
          requireNonNullableReceiver(owner, assignment.receiver().type(), "field assignment");
          CoreType fieldType =
              instantiatedFieldType(owner, assignment.receiver().type(), assignment.field());
          requireAssignable(owner, fieldType, owner, assignment.value().type(), "field assignment");
        }
        case CoreStatement.IntrinsicAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          assignment.index().ifPresent(value -> verifyExpression(owner, callable, value));
          verifyExpression(owner, callable, assignment.value());
          verifyIntrinsicAssignment(owner, assignment);
        }
        case CoreStatement.ExpressionStatement expression ->
            verifyExpression(owner, callable, expression.expression());
        case CoreStatement.IfStatement conditional -> {
          verifyExpression(owner, callable, conditional.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, conditional.condition().type(), "if condition");
          verifyBlock(owner, callable, conditional.thenBlock());
          verifyBlock(owner, callable, conditional.elseBlock());
        }
        case CoreStatement.ConditionalForStatement loop -> {
          verifyExpression(owner, callable, loop.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, loop.condition().type(), "loop condition");
          controls.addFirst(Control.loop());
          verifyBlock(owner, callable, loop.body());
          controls.removeFirst();
        }
        case CoreStatement.ForStatement loop -> {
          if (local(callable, loop.iteratorLocal()).kind() != CoreLocal.Kind.ITERATOR
              || local(callable, loop.variableLocal()).kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("for loop local ABI is invalid");
          }
          loop.indexLocal()
              .ifPresent(
                  index -> {
                    if (index == loop.iteratorLocal() || index == loop.variableLocal()) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                    CoreLocal local = local(callable, index);
                    if (local.kind() != CoreLocal.Kind.VARIABLE
                        || !absolute(owner, local.type()).equals(CoreType.INTEGER)) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                  });
          verifyExpression(owner, callable, loop.iterable());
          verifyIteration(owner, callable, loop);
          controls.addFirst(Control.loop());
          verifyBlock(owner, callable, loop.body());
          controls.removeFirst();
        }
        case CoreStatement.ReturnStatement returned -> {
          if (returned.value().isEmpty()) {
            requireSameType(owner, CoreType.VOID, owner, callable.returnType(), "return");
          } else {
            CoreExpression value = returned.value().orElseThrow();
            verifyExpression(owner, callable, value);
            requireAssignable(owner, callable.returnType(), owner, value.type(), "return");
          }
        }
        case CoreStatement.YieldStatement yielded -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.SWITCH) {
            throw new IllegalArgumentException("yield is only valid inside switch");
          }
          verifyExpression(owner, callable, yielded.value());
          requireAssignable(
              owner,
              controls.getFirst().yieldType(),
              owner,
              yielded.value().type(),
              "switch yield");
        }
        case CoreStatement.BreakStatement ignored -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.LOOP) {
            throw new IllegalArgumentException("break is only valid inside loop");
          }
        }
        case CoreStatement.ContinueStatement ignored -> {
          if (controls.stream().noneMatch(value -> value.kind() == ControlKind.LOOP)) {
            throw new IllegalArgumentException("continue is only valid inside loop");
          }
        }
      }
    }
  }

  private void verifyExpression(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression expression) {
    if (expression instanceof CoreExpression.Call
        || expression instanceof CoreExpression.InterfaceCall
        || expression instanceof CoreExpression.Intrinsic
        || expression.type().equals(CoreType.VOID)) {
      verifyReturnType(owner, expression.type(), callable.reifiedTypeLocals().size());
    } else {
      verifyValueType(owner, expression.type(), callable.reifiedTypeLocals().size());
    }
    switch (expression) {
      case CoreExpression.Literal literal -> verifyLiteral(owner, literal);
      case CoreExpression.NullLiteral literal -> {
        if (!literal.type().isNullable()) {
          throw new IllegalArgumentException("null literal requires a nullable core type");
        }
      }
      case CoreExpression.CollectionLiteral collection -> {
        verifyRuntimeType(owner, callable, collection.runtimeType());
        requireSameType(
            owner,
            collection.runtimeType().template(),
            owner,
            collection.type(),
            "collection runtime type");
        if (!matchesCollectionLiteral(owner, collection)) {
          throw new IllegalArgumentException("collection literal does not match its builtin ABI");
        }
        collection.elements().forEach(value -> verifyExpression(owner, callable, value));
        CoreType absoluteType = absolute(owner, collection.type());
        if (!(absoluteType instanceof CoreType.Declared declared)
            || declared.arguments().size() != 1) {
          throw new IllegalArgumentException("collection literal requires one element type");
        }
        CoreType elementType = declared.arguments().getFirst();
        collection
            .elements()
            .forEach(
                value ->
                    requireAssignable(
                        owner, elementType, owner, value.type(), "collection element"));
      }
      case CoreExpression.LocalRead read -> {
        CoreLocal local = local(callable, read.localIndex());
        requireAssignable(owner, local.type(), owner, read.type(), "local read");
      }
      case CoreExpression.FieldRead read -> {
        verifyExpression(owner, callable, read.receiver());
        verifyReceiverSafety(owner, read.receiver().type(), read.nullSafe(), "field read");
        CoreType fieldType = instantiatedFieldType(owner, read.receiver().type(), read.field());
        requireSameType(
            owner,
            safeResult(fieldType, read.nullSafe(), read.receiver().type()),
            owner,
            read.type(),
            "field read");
      }
      case CoreExpression.EnumConstruct construct ->
          verifyEnumConstruct(owner, callable, construct);
      case CoreExpression.Unary unary -> {
        verifyExpression(owner, callable, unary.operand());
        verifyUnary(owner, unary);
      }
      case CoreExpression.Binary binary -> {
        verifyExpression(owner, callable, binary.left());
        verifyExpression(owner, callable, binary.right());
        verifyBinary(owner, binary);
      }
      case CoreExpression.Switch switched -> verifySwitch(owner, callable, switched);
      case CoreExpression.Index index -> {
        verifyExpression(owner, callable, index.receiver());
        verifyExpression(owner, callable, index.index());
        verifyIndex(owner, index);
      }
      case CoreExpression.CopyObject copied -> {
        verifyExpression(owner, callable, copied.receiver());
        verifyReceiverSafety(owner, copied.receiver().type(), copied.nullSafe(), "copy");
        CoreType receiver = nonNullable(absolute(owner, copied.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("copy requires an identity receiver");
        }
        requireSameType(
            owner,
            safeResult(copied.receiver().type(), copied.nullSafe(), copied.receiver().type()),
            owner,
            copied.type(),
            "copy result");
      }
      case CoreExpression.Call call -> verifyCall(owner, callable, call);
      case CoreExpression.InterfaceCall call -> verifyInterfaceCall(owner, callable, call);
      case CoreExpression.Construct construct -> verifyConstruct(owner, callable, construct);
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> verifyExpression(owner, callable, value));
        intrinsic
            .arguments()
            .forEach(argument -> verifyExpression(owner, callable, argument.value()));
        intrinsic.runtimeType().ifPresent(type -> verifyRuntimeType(owner, callable, type));
        verifyIntrinsic(owner, intrinsic);
      }
    }
  }

  private void verifyLiteral(DefinitionId owner, CoreExpression.Literal literal) {
    CoreType expected =
        switch (literal.value()) {
          case Integer ignored ->
              absolute(owner, literal.type()).equals(CoreType.CODE_POINT)
                  ? CoreType.CODE_POINT
                  : CoreType.INTEGER;
          case Long ignored -> CoreType.LONG;
          case Float ignored -> CoreType.FLOAT;
          case Double ignored -> CoreType.DOUBLE;
          case Boolean ignored -> CoreType.BOOLEAN;
          case String ignored -> CoreType.STRING;
          default -> throw new IllegalArgumentException("unsupported core literal value");
        };
    requireSameType(owner, expected, owner, literal.type(), "literal");
  }

  private void verifyUnary(DefinitionId owner, CoreExpression.Unary unary) {
    CoreType expected = absolute(owner, unary.operand().type());
    if (unary.operator() == CoreUnaryOperator.NOT) expected = CoreType.BOOLEAN;
    else if (!isNumericLeaf(expected)) {
      throw new IllegalArgumentException("numeric unary operand requires a concrete leaf type");
    }
    requireSameType(owner, expected, owner, unary.operand().type(), "unary operand");
    requireSameType(owner, expected, owner, unary.type(), "unary result");
  }

  private void verifyBinary(DefinitionId owner, CoreExpression.Binary binary) {
    CoreType left = absolute(owner, binary.left().type());
    CoreType right = absolute(owner, binary.right().type());
    CoreType result = absolute(owner, binary.type());
    switch (binary.operator()) {
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER -> {
        if (!isNumericLeaf(left)) {
          throw new IllegalArgumentException("binary left operand requires a numeric leaf type");
        }
        requireSameType(left, right, "binary right operand");
        requireSameType(left, result, "binary result");
      }
      case STRING_CONCAT -> {
        requireSameType(CoreType.STRING, left, "binary left operand");
        requireSameType(CoreType.STRING, right, "binary right operand");
        requireSameType(CoreType.STRING, result, "binary result");
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        if (!isNumericLeaf(left)) {
          throw new IllegalArgumentException(
              "comparison left operand requires a numeric leaf type");
        }
        requireSameType(left, right, "comparison right operand");
        requireSameType(CoreType.BOOLEAN, result, "comparison result");
      }
      case AND, OR -> {
        requireSameType(CoreType.BOOLEAN, left, "logical left operand");
        requireSameType(CoreType.BOOLEAN, right, "logical right operand");
        requireSameType(CoreType.BOOLEAN, result, "logical result");
      }
      case EQUAL, NOT_EQUAL -> {
        if (!isAssignable(left, right) && !isAssignable(right, left)) {
          throw new IllegalArgumentException("equality operands have incompatible types");
        }
        requireSameType(CoreType.BOOLEAN, result, "equality result");
      }
      case COALESCE -> {
        if (!mayContainNull(left)) {
          throw new IllegalArgumentException("coalesce left operand cannot contain null");
        }
        CoreType expected = nonNullable(left);
        requireAssignable(expected, right, "coalesce right operand");
        requireSameType(expected, result, "coalesce result");
      }
    }
  }

  private void verifyIndex(DefinitionId owner, CoreExpression.Index index) {
    requireNonNullableReceiver(owner, index.receiver().type(), "index read");
    boolean valid =
        builtins.indexCandidates(index.readIntrinsic()).stream()
            .anyMatch(candidate -> matchesIndex(owner, index, candidate));
    if (!valid)
      throw new IllegalArgumentException("index expression does not match its builtin ABI");
  }

  private boolean matchesCollectionLiteral(
      DefinitionId owner, CoreExpression.CollectionLiteral collection) {
    CoreType actual = absolute(owner, collection.type());
    for (BuiltinCatalog.IntrinsicCandidate candidate :
        builtins.intrinsicCandidates(collection.materializer())) {
      if (candidate.receiver().isPresent()
          || !candidate.parameters().isEmpty()
          || !candidate.runtimeType()) {
        continue;
      }
      Map<String, CoreType> substitutions = new LinkedHashMap<>();
      if (bindPattern(actual, candidate.result(), substitutions)) return true;
    }
    return false;
  }

  private boolean matchesIndex(
      DefinitionId owner, CoreExpression.Index index, BuiltinCatalog.IndexCandidate candidate) {
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, index.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (!candidate.writeIntrinsic().equals(index.writeIntrinsic())) return false;
    CoreType expectedIndex = instantiate(candidate.index(), substitutions);
    CoreType expectedResult = instantiate(candidate.result(), substitutions);
    return isAssignable(expectedIndex, absolute(owner, index.index().type()))
        && expectedResult.equals(absolute(owner, index.type()));
  }

  private void verifyIntrinsic(DefinitionId owner, CoreExpression.Intrinsic intrinsic) {
    boolean valid =
        builtins.intrinsicCandidates(intrinsic.intrinsic()).stream()
            .anyMatch(candidate -> matchesIntrinsic(owner, intrinsic, candidate));
    if (!valid) {
      throw new IllegalArgumentException("intrinsic expression does not match its builtin ABI");
    }
  }

  private boolean matchesIntrinsic(
      DefinitionId owner,
      CoreExpression.Intrinsic intrinsic,
      BuiltinCatalog.IntrinsicCandidate candidate) {
    if (candidate.receiver().isPresent() != intrinsic.receiver().isPresent()
        || candidate.runtimeType() != intrinsic.runtimeType().isPresent()
        || intrinsic.nullSafe() && intrinsic.receiver().isEmpty()
        || !denseArguments(intrinsic.arguments(), candidate.parameters().size())) {
      return false;
    }
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (candidate.receiver().isPresent()) {
      CoreType actualReceiver = absolute(owner, intrinsic.receiver().orElseThrow().type());
      if (!intrinsic.nullSafe() && actualReceiver.isNullable()) return false;
      if (!bindPattern(
          nonNullable(actualReceiver), candidate.receiver().orElseThrow(), substitutions)) {
        return false;
      }
    }
    if (candidate.runtimeType()) {
      CoreType runtimeTemplate = absolute(owner, intrinsic.runtimeType().orElseThrow().template());
      if (!bindPattern(runtimeTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      SemanticType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType expected = instantiate(parameter, substitutions);
      if (!expected.equals(CoreType.DYNAMIC)
          && !isAssignable(expected, absolute(owner, argument.value().type()))) {
        return false;
      }
    }
    CoreType result = instantiate(candidate.result(), substitutions);
    CoreType receiver =
        intrinsic
            .receiver()
            .map(CoreExpression::type)
            .map(type -> absolute(owner, type))
            .orElse(CoreType.DYNAMIC);
    result = safeResult(result, intrinsic.nullSafe(), receiver);
    return result.equals(absolute(owner, intrinsic.type()));
  }

  private void verifyIntrinsicAssignment(
      DefinitionId owner, CoreStatement.IntrinsicAssignment assignment) {
    requireNonNullableReceiver(owner, assignment.receiver().type(), "intrinsic assignment");
    boolean valid =
        builtins.writeCandidates(assignment.intrinsic()).stream()
            .anyMatch(candidate -> matchesWrite(owner, assignment, candidate));
    if (!valid) {
      throw new IllegalArgumentException("intrinsic assignment does not match its builtin ABI");
    }
  }

  private boolean matchesWrite(
      DefinitionId owner,
      CoreStatement.IntrinsicAssignment assignment,
      BuiltinCatalog.WriteCandidate candidate) {
    if (candidate.index().isPresent() != assignment.index().isPresent()) return false;
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, assignment.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (candidate.index().isPresent()) {
      CoreType expectedIndex = instantiate(candidate.index().orElseThrow(), substitutions);
      if (!isAssignable(expectedIndex, absolute(owner, assignment.index().orElseThrow().type()))) {
        return false;
      }
    }
    CoreType expectedValue = instantiate(candidate.value(), substitutions);
    return isAssignable(expectedValue, absolute(owner, assignment.value().type()));
  }

  private void verifyIteration(
      DefinitionId owner, CoreDefinition.Callable callable, CoreStatement.ForStatement loop) {
    requireNonNullableReceiver(owner, loop.iterable().type(), "iteration");
    CoreType variable = absolute(owner, local(callable, loop.variableLocal()).type());
    switch (loop.iteration()) {
      case CoreIteration.Builtin builtin -> {
        boolean valid =
            builtins.iterationCandidates(builtin.intrinsic()).stream()
                .anyMatch(
                    candidate -> {
                      Map<String, CoreType> substitutions = new LinkedHashMap<>();
                      return bindPattern(
                              nonNullable(absolute(owner, loop.iterable().type())),
                              candidate.receiver(),
                              substitutions)
                          && instantiate(candidate.element(), substitutions).equals(variable);
                    });
        if (!valid) {
          throw new IllegalArgumentException("iteration does not match its builtin ABI");
        }
      }
      case CoreIteration.Interface protocol -> {
        CoreType iterator =
            verifyIterationRequirement(
                owner, callable, loop.iterable().type(), protocol.iteratorRequirement());
        CoreType hasNext =
            verifyIterationRequirement(owner, callable, iterator, protocol.hasNextRequirement());
        CoreType next =
            verifyIterationRequirement(owner, callable, iterator, protocol.nextRequirement());
        requireSameType(CoreType.BOOLEAN, hasNext, "iteration hasNext result");
        requireSameType(variable, next, "iteration next result");
      }
    }
  }

  private CoreType verifyIterationRequirement(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreType receiverType,
      CoreDefinitionLink requirementLink) {
    DefinitionId requirementId = resolve(owner, requirementLink);
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.InterfaceMethod requirement)
        || !requirement.typeParameters().isEmpty()
        || !requirement.parameterTypes().isEmpty()) {
      throw new IllegalArgumentException("iteration requirement has the wrong callable ABI");
    }
    InterfaceInstance required =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    CoreType actualReceiver = nonNullable(absolute(owner, receiverType));
    CoreType realized = realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          callable.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        realized =
            realizedInterface(
                nonNullable(absolute(owner, declaration.upperBound().orElseThrow())),
                required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    InterfaceInstance receiver = interfaceInstance(owner, realized);
    List<CoreType> substitutions = interfaceSubstitutions(receiver, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    return absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
  }

  private boolean bindPattern(
      CoreType actual, SemanticType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == SemanticType.Kind.ERROR) return true;
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      CoreType captured = pattern.isNullable() ? nonNullable(actual) : actual;
      CoreType previous = substitutions.putIfAbsent(pattern.identity(), captured);
      return previous == null || previous.equals(captured);
    }
    if (pattern.kind() == SemanticType.Kind.VOID) return actual.equals(CoreType.VOID);
    if (pattern.kind() == SemanticType.Kind.NULL) return actual.equals(CoreType.NULL);
    if (!(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(pattern.identity())
        || declared.arguments().size() != pattern.arguments().size()
        || declared.category() != category(pattern.category())
        || declared.isNullable() != pattern.isNullable()) {
      return false;
    }
    for (int index = 0; index < pattern.arguments().size(); index++) {
      if (!bindPattern(
          declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
        return false;
      }
    }
    return true;
  }

  private CoreType instantiate(SemanticType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType type = substitutions.get(pattern.identity());
        if (type == null)
          throw new IllegalArgumentException("builtin type parameter is unresolved");
        yield pattern.isNullable() ? type.asNullable() : type;
      }
      case DECLARED ->
          new CoreType.Declared(
              new CoreTypeConstructor.Builtin(new BuiltinTypeId(pattern.identity())),
              pattern.arguments().stream()
                  .map(argument -> instantiate(argument, substitutions))
                  .toList(),
              category(pattern.category()),
              pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      case VOID -> CoreType.VOID;
      case NULL -> CoreType.NULL;
      case ERROR -> CoreType.DYNAMIC;
    };
  }

  private static CoreValueCategory category(ValueCategory category) {
    return switch (category) {
      case VALUE -> CoreValueCategory.VALUE;
      case IDENTITY -> CoreValueCategory.IDENTITY;
      case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
      case DYNAMIC -> CoreValueCategory.DYNAMIC;
      case VOID -> CoreValueCategory.VOID;
    };
  }

  private void verifyReceiverSafety(
      DefinitionId owner, CoreType receiver, boolean nullSafe, String subject) {
    CoreType actual = absolute(owner, receiver);
    if (!nullSafe && actual.isNullable()) {
      throw new IllegalArgumentException(subject + " requires null-safe access");
    }
  }

  private void requireNonNullableReceiver(DefinitionId owner, CoreType receiver, String subject) {
    if (absolute(owner, receiver).isNullable()) {
      throw new IllegalArgumentException(subject + " requires a non-null receiver");
    }
  }

  private void verifyCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Call call) {
    DefinitionId targetId = resolve(owner, call.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Callable target)) {
      throw new IllegalArgumentException("call target is not callable");
    }
    call.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (target.hasReceiver() != call.receiver().isPresent()) {
      throw new IllegalArgumentException("call receiver does not match the target ABI");
    }
    if (call.nullSafe() && call.receiver().isEmpty()) {
      throw new IllegalArgumentException("null-safe call requires a receiver");
    }
    List<CoreType> substitutions = new ArrayList<>();
    if (target.hasReceiver()) {
      CoreType actualReceiver = absolute(owner, call.receiver().orElseThrow().type());
      CoreType nonNullableReceiver = nonNullable(actualReceiver);
      if (!(nonNullableReceiver instanceof CoreType.Declared declared)) {
        throw new IllegalArgumentException("method receiver is not a declared type");
      }
      substitutions.addAll(declared.arguments());
      CoreType expectedReceiver =
          absolute(targetId, target.receiverType().orElseThrow()).substitute(substitutions::get);
      if (!expectedReceiver.equals(nonNullableReceiver)) {
        throw new IllegalArgumentException("method receiver type does not match the target ABI");
      }
      if (!call.nullSafe() && actualReceiver.isNullable()) {
        throw new IllegalArgumentException("nullable receiver requires a null-safe call");
      }
    }
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedTypeLocals().size()) {
      throw new IllegalArgumentException("call reified arguments do not match the target ABI");
    }
    verifyTypeArgumentBounds(targetId, target.typeParameters(), substitutions);
    verifyDenseArguments(call.arguments(), target.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          absolute(targetId, target.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "call argument");
    }
    CoreType result = absolute(targetId, target.returnType()).substitute(substitutions::get);
    CoreType receiverType = call.receiver().map(CoreExpression::type).orElse(CoreType.DYNAMIC);
    result = safeResult(result, call.nullSafe(), receiverType);
    requireSameType(result, absolute(owner, call.type()), "call result");
  }

  private void verifyInterfaceCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.InterfaceCall call) {
    DefinitionId requirementId = resolve(owner, call.requirement());
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.InterfaceMethod requirement)) {
      throw new IllegalArgumentException("interface call target is not an interface method");
    }
    verifyExpression(owner, caller, call.receiver());
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    verifyReceiverSafety(owner, call.receiver().type(), call.nullSafe(), "interface call");
    CoreType actualReceiver = nonNullable(absolute(owner, call.receiver().type()));
    InterfaceInstance required =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    CoreType realized = realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          caller.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        CoreType upperBound = absolute(owner, declaration.upperBound().orElseThrow());
        realized = realizedInterface(nonNullable(upperBound), required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    InterfaceInstance instance = interfaceInstance(owner, realized);
    List<CoreType> substitutions = interfaceSubstitutions(instance, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    substitutions = new ArrayList<>(substitutions);
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (call.reifiedArguments().size() != requirement.typeParameters().size()) {
      throw new IllegalArgumentException(
          "interface call type arguments do not match the requirement ABI");
    }
    verifyTypeArgumentBounds(requirementId, requirement.typeParameters(), substitutions);
    verifyDenseArguments(call.arguments(), requirement.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          absolute(requirementId, requirement.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(
          expected, absolute(owner, argument.value().type()), "interface call argument");
    }
    CoreType result =
        absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
    result = safeResult(result, call.nullSafe(), call.receiver().type());
    requireSameType(result, absolute(owner, call.type()), "interface call result");
  }

  private void verifyConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Construct construct) {
    DefinitionId targetId = resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Class target)) {
      throw new IllegalArgumentException("construct target is not a class");
    }
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType constructedType = absolute(owner, construct.type());
    requireSameType(
        constructedType,
        absolute(owner, construct.runtimeType().template()),
        "construct runtime type");
    if (!(nonNullable(constructedType) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("constructed type does not match the class ABI");
    }
    verifyDenseArguments(construct.arguments(), target.fields().size());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          absolute(targetId, target.fields().get(argument.parameterIndex()).type())
              .substitute(declared.arguments()::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "constructor argument");
    }
  }

  private void verifyEnumConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.EnumConstruct construct) {
    DefinitionId targetId = resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Enum target)) {
      throw new IllegalArgumentException("enum construct target is not an enum");
    }
    CoreEnumVariant variant =
        target.variants().stream()
            .filter(candidate -> candidate.key().equals(construct.variantKey()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("enum construct variant is absent"));
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType type = absolute(owner, construct.type());
    requireSameType(
        type, absolute(owner, construct.runtimeType().template()), "enum construct runtime type");
    if (type.isNullable()
        || !(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("enum construct type does not match its target");
    }
    verifyDenseArguments(construct.arguments(), variant.fields().size());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          absolute(targetId, variant.fields().get(argument.parameterIndex()).type())
              .substitute(declared.arguments()::get);
      requireAssignable(
          expected, absolute(owner, argument.value().type()), "enum construct argument");
    }
  }

  private void verifySwitch(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression.Switch switched) {
    verifyExpression(owner, callable, switched.value());
    CoreType valueType = absolute(owner, switched.value().type());
    PatternCoverage<CoreType> coverage = new PatternCoverage<>(new CorePatternDomain(owner));
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    for (CoreSwitchCase switchCase : switched.cases()) {
      PatternCoverage.Pattern pattern =
          verifyPattern(owner, callable, switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        throw new IllegalArgumentException("core switch case is unreachable");
      }
      previous.add(pattern);
      controls.addFirst(Control.switched(switched.type()));
      verifyBlock(owner, callable, switchCase.body());
      controls.removeFirst();
      if (!switched.type().equals(CoreType.VOID) && !definitelyYields(switchCase.body())) {
        throw new IllegalArgumentException("core switch expression case does not yield");
      }
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      throw new IllegalArgumentException("core switch is not exhaustive");
    }
  }

  private PatternCoverage.Pattern verifyPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    if (expected.isNullable() && !(pattern instanceof CorePattern.Null)) {
      if (pattern instanceof CorePattern.Wildcard) return PatternCoverage.Pattern.any();
      return PatternCoverage.Pattern.constructor(
          "$value", List.of(verifyNonNullPattern(owner, callable, pattern, nonNullable(expected))));
    }
    if (pattern instanceof CorePattern.Null) {
      if (!expected.isNullable()) {
        throw new IllegalArgumentException("core null pattern requires nullable type");
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return verifyNonNullPattern(owner, callable, pattern, nonNullable(expected));
  }

  private PatternCoverage.Pattern verifyNonNullPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    return switch (pattern) {
      case CorePattern.Wildcard ignored -> PatternCoverage.Pattern.any();
      case CorePattern.Binding binding -> {
        CoreLocal local = local(callable, binding.localIndex());
        if (local.kind() != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("core pattern binding requires variable local");
        }
        verifyValueType(owner, binding.type(), callable.reifiedTypeLocals().size());
        requireSameType(owner, binding.type(), owner, local.type(), "pattern binding local");
        requireSameType(expected, absolute(owner, binding.type()), "pattern binding type");
        yield PatternCoverage.Pattern.any();
      }
      case CorePattern.Variant variant -> {
        EnumInstance instance = enumInstance(expected);
        CoreEnumVariant declaration =
            instance.declaration().variants().stream()
                .filter(candidate -> candidate.key().equals(variant.variantKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("core pattern variant is absent"));
        if (variant.arguments().size() != declaration.fields().size()) {
          throw new IllegalArgumentException("core pattern variant has wrong payload arity");
        }
        List<PatternCoverage.Pattern> arguments = new ArrayList<>();
        for (int index = 0; index < variant.arguments().size(); index++) {
          CoreType payloadType =
              absolute(instance.definition(), declaration.fields().get(index).type())
                  .substitute(instance.type().arguments()::get);
          arguments.add(
              verifyPattern(owner, callable, variant.arguments().get(index), payloadType));
        }
        yield PatternCoverage.Pattern.constructor("variant:" + variant.variantKey(), arguments);
      }
      case CorePattern.Literal literal -> {
        CoreType literalType =
            switch (literal.value()) {
              case Integer ignored ->
                  literal.type().equals(CoreType.CODE_POINT)
                      ? CoreType.CODE_POINT
                      : CoreType.INTEGER;
              case Long ignored -> CoreType.LONG;
              case Float ignored -> CoreType.FLOAT;
              case Double ignored -> CoreType.DOUBLE;
              case Boolean ignored -> CoreType.BOOLEAN;
              case String ignored -> CoreType.STRING;
              default -> throw new IllegalArgumentException("unsupported core pattern literal");
            };
        requireSameType(literal.type(), literalType, "pattern literal representation");
        requireSameType(expected, literal.type(), "pattern literal");
        yield PatternCoverage.Pattern.constructor(literalKey(expected, literal.value()), List.of());
      }
      case CorePattern.Null ignored ->
          throw new IllegalArgumentException("core null pattern requires nullable type");
    };
  }

  private EnumInstance enumInstance(CoreType type) {
    if (!(nonNullable(type) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    DefinitionId definition = resolveExternal(user.definition());
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Enum enumDeclaration)
        || declared.arguments().size() != enumDeclaration.typeParameters().size()) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    return new EnumInstance(definition, enumDeclaration, declared);
  }

  private static String literalKey(CoreType expected, Object value) {
    if (isNumericLeaf(expected)) {
      return "numeric:" + expected + ":" + value;
    }
    if (expected.equals(CoreType.CODE_POINT)) return "codepoint:" + value;
    if (expected.equals(CoreType.BOOLEAN)) return "boolean:" + value;
    return "string:" + value;
  }

  private static boolean definitelyYields(CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      if (statement instanceof CoreStatement.ReturnStatement
          || statement instanceof CoreStatement.YieldStatement) {
        return true;
      }
      if (statement instanceof CoreStatement.IfStatement conditional
          && definitelyYields(conditional.thenBlock())
          && definitelyYields(conditional.elseBlock())) {
        return true;
      }
    }
    return false;
  }

  private final class CorePatternDomain implements PatternCoverage.Domain<CoreType> {
    private final DefinitionId owner;

    private CorePatternDomain(DefinitionId owner) {
      this.owner = owner;
    }

    @Override
    public List<PatternCoverage.Constructor<CoreType>> constructors(CoreType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>("$value", List.of(nonNullable(type))));
      }
      if (type.equals(CoreType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      CoreType absoluteType = absolute(owner, type);
      if (!(absoluteType instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
        return List.of();
      }
      DefinitionId definition = resolveExternal(user.definition());
      CoreDefinition target = program.definition(definition).orElseThrow();
      if (!(target instanceof CoreDefinition.Enum enumDeclaration)) return List.of();
      return enumDeclaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.key(),
                      variant.fields().stream()
                          .map(
                              field ->
                                  absolute(definition, field.type())
                                      .substitute(declared.arguments()::get))
                          .toList()))
          .toList();
    }

    @Override
    public PatternCoverage.Constructor<CoreType> openConstructor(CoreType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }

  private record EnumInstance(
      DefinitionId definition, CoreDefinition.Enum declaration, CoreType.Declared type) {}

  private enum ControlKind {
    LOOP,
    SWITCH
  }

  private record Control(ControlKind kind, CoreType yieldType) {
    static Control loop() {
      return new Control(ControlKind.LOOP, CoreType.VOID);
    }

    static Control switched(CoreType type) {
      return new Control(ControlKind.SWITCH, type);
    }
  }

  private CoreType instantiatedFieldType(
      DefinitionId owner, CoreType receiverType, CoreFieldReference reference) {
    DefinitionId targetId = resolve(owner, reference.owner());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Class target)
        || reference.ordinal() >= target.fields().size()) {
      throw new IllegalArgumentException("field owner or ordinal is invalid");
    }
    CoreType receiver = nonNullable(absolute(owner, receiverType));
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("field receiver does not match its owner");
    }
    return absolute(targetId, target.fields().get(reference.ordinal()).type())
        .substitute(declared.arguments()::get);
  }

  private void verifyRuntimeType(
      DefinitionId owner, CoreDefinition.Callable callable, CoreRuntimeType runtimeType) {
    verifyRuntimeTypeTemplate(owner, runtimeType.template(), callable.reifiedTypeLocals().size());
    Set<Integer> parameters = new HashSet<>();
    collectTypeParameters(runtimeType.template(), parameters);
    Set<Integer> captures = new HashSet<>();
    for (CoreTypeCapture capture : runtimeType.captures()) {
      if (!captures.add(capture.typeParameterIndex())) {
        throw new IllegalArgumentException("runtime type captures must be unique");
      }
      if (capture.typeParameterIndex() >= callable.reifiedTypeLocals().size()
          || capture.localIndex() != callable.reifiedTypeLocals().get(capture.typeParameterIndex())
          || local(callable, capture.localIndex()).kind() != CoreLocal.Kind.REIFIED_TYPE) {
        throw new IllegalArgumentException("runtime type capture does not match a reified local");
      }
    }
    if (!captures.equals(parameters)) {
      throw new IllegalArgumentException("runtime type captures do not cover the template");
    }
  }

  private void verifyValueType(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "core value ABI");
  }

  private void verifyReturnType(DefinitionId owner, CoreType type, int parameterCount) {
    if (type.equals(CoreType.VOID)) return;
    verifyInhabitedType(owner, type, parameterCount, "core return ABI");
  }

  private void verifyRuntimeTypeTemplate(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "runtime type template");
  }

  private void verifyTypeParameters(
      DefinitionId owner, List<CoreTypeParameter> parameters, int parameterCount) {
    for (CoreTypeParameter parameter : parameters) {
      parameter
          .upperBound()
          .ifPresent(
              bound -> {
                verifyValueType(owner, bound, parameterCount);
                interfaceInstance(owner, bound);
              });
    }
  }

  private void verifyTypeArgumentBounds(
      DefinitionId owner, List<CoreTypeParameter> parameters, List<CoreType> substitutions) {
    for (CoreTypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      CoreType expected =
          absolute(owner, parameter.upperBound().orElseThrow()).substitute(substitutions::get);
      CoreType actual = substitutions.get(parameter.index());
      requireAssignable(expected, actual, "type argument bound");
    }
  }

  private void verifyLocalType(DefinitionId owner, CoreLocal local, int parameterCount) {
    if (local.kind() == CoreLocal.Kind.REIFIED_TYPE || local.kind() == CoreLocal.Kind.ITERATOR) {
      if (!local.type().equals(CoreType.DYNAMIC)) {
        throw new IllegalArgumentException("internal runtime locals require dynamic type");
      }
      return;
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
        declared
            .arguments()
            .forEach(argument -> verifyInhabitedType(owner, argument, parameterCount, subject));
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin -> verifyBuiltinType(builtin, declared);
          case CoreTypeConstructor.User user -> verifyUserType(owner, user, declared);
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
    if (target instanceof CoreDefinition.Class declaration) {
      arity = declaration.typeParameters().size();
      category = CoreValueCategory.IDENTITY;
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
          case CoreDefinition.Class declaration -> declaration.typeParameters();
          case CoreDefinition.Enum declaration -> declaration.typeParameters();
          case CoreDefinition.Interface declaration -> declaration.typeParameters();
          default -> throw new IllegalStateException("nominal definition kind changed");
        };
    verifyTypeArgumentBounds(targetId, parameters, declared.arguments());
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

  private static void collectTypeParameters(CoreType type, Set<Integer> result) {
    switch (type) {
      case CoreType.Parameter parameter -> result.add(parameter.index());
      case CoreType.Declared declared ->
          declared.arguments().forEach(argument -> collectTypeParameters(argument, result));
      case CoreType.Special ignored -> {}
    }
  }

  private static void verifyDenseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (!denseArguments(arguments, parameterCount)) {
      throw new IllegalArgumentException("core arguments do not match the target arity");
    }
  }

  private static boolean denseArguments(List<CoreArgument> arguments, int parameterCount) {
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

  private static CoreLocal local(CoreDefinition.Callable callable, int index) {
    if (index < 0 || index >= callable.locals().size()) {
      throw new IllegalArgumentException("core local use is outside the local table");
    }
    return callable.locals().get(index);
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("core program contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  private static DefinitionId resolveExternal(CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference.External external)) {
      throw new IllegalArgumentException("absolute core type contains a relative reference");
    }
    return external.definition();
  }

  private CoreType absolute(DefinitionId owner, CoreType type) {
    return CoreTypes.absolute(type, owner, program);
  }

  private void requireAssignable(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireAssignable(absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  private void requireAssignable(CoreType expected, CoreType actual, String subject) {
    if (!isAssignable(expected, actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  private void requireSameType(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireSameType(absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  private static void requireSameType(CoreType expected, CoreType actual, String subject) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  private boolean isAssignable(CoreType expected, CoreType actual) {
    if (expected.equals(CoreType.DYNAMIC) || actual.equals(CoreType.DYNAMIC)) return true;
    if (actual.equals(CoreType.NULL)) return expected.isNullable();
    if (expected.equals(CoreType.NULL)) return actual.equals(CoreType.NULL);
    CoreType expectedValue = nonNullable(expected);
    CoreType actualValue = nonNullable(actual);
    boolean compatible =
        expectedValue.equals(actualValue)
            || expectedValue.equals(CoreType.NUMBER) && isNumericLeaf(actualValue);
    if (!compatible
        && expectedValue instanceof CoreType.Declared expectedDeclared
        && expectedDeclared.category() == CoreValueCategory.POLYMORPHIC
        && expectedDeclared.constructor() instanceof CoreTypeConstructor.User expectedUser) {
      DefinitionId expectedInterface = resolveExternal(expectedUser.definition());
      CoreType realized = realizedInterface(actualValue, expectedInterface);
      compatible = expectedValue.equals(realized);
    }
    return compatible && (expected.isNullable() || !actual.isNullable());
  }

  private static boolean isNumericLeaf(CoreType type) {
    CoreType value = nonNullable(type);
    return value.equals(CoreType.INTEGER)
        || value.equals(CoreType.LONG)
        || value.equals(CoreType.FLOAT)
        || value.equals(CoreType.DOUBLE);
  }

  private CoreType realizedInterface(CoreType actual, DefinitionId target) {
    if (actual instanceof CoreType.Declared builtinActual
        && builtinActual.constructor() instanceof CoreTypeConstructor.Builtin) {
      for (CoreDefinitionRecord record : program.definitions()) {
        if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance))
          continue;
        Map<Integer, CoreType> substitutions = new LinkedHashMap<>();
        if (!matchCoreTemplate(
            absolute(record.id(), conformance.concreteBuiltinType()),
            builtinActual,
            substitutions)) {
          continue;
        }
        CoreType interfaceType =
            absolute(record.id(), conformance.interfaceType()).substitute(substitutions::get);
        InterfaceInstance instance = interfaceInstance(record.id(), interfaceType);
        List<CoreType> arguments = interfaceSubstitutions(instance, target);
        if (arguments != null) return interfaceType(target, arguments);
      }
      return null;
    }
    if (!(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      return null;
    }
    DefinitionId actualId = resolveExternal(user.definition());
    CoreDefinition definition = program.definition(actualId).orElseThrow();
    if (definition instanceof CoreDefinition.Interface interfaceDefinition) {
      List<CoreType> arguments =
          interfaceSubstitutions(
              new InterfaceInstance(actualId, interfaceDefinition, declared), target);
      if (arguments == null) return null;
      return interfaceType(target, arguments);
    }
    if (!(definition instanceof CoreDefinition.Class classDefinition)) return null;
    for (CoreConformance conformance : classDefinition.conformances()) {
      CoreType instantiated =
          absolute(actualId, conformance.interfaceType()).substitute(declared.arguments()::get);
      InterfaceInstance instance = interfaceInstance(actualId, instantiated);
      List<CoreType> arguments = interfaceSubstitutions(instance, target);
      if (arguments != null) return interfaceType(target, arguments);
    }
    return null;
  }

  private static boolean matchCoreTemplate(
      CoreType pattern, CoreType actual, Map<Integer, CoreType> substitutions) {
    if (pattern instanceof CoreType.Parameter parameter) {
      CoreType value =
          parameter.nullability() == CoreNullability.NULLABLE ? nonNullable(actual) : actual;
      CoreType previous = substitutions.putIfAbsent(parameter.index(), value);
      return previous == null || previous.equals(value);
    }
    if (!(pattern instanceof CoreType.Declared expected)
        || !(actual instanceof CoreType.Declared found)
        || !expected.constructor().equals(found.constructor())
        || expected.category() != found.category()
        || expected.nullability() != found.nullability()
        || expected.arguments().size() != found.arguments().size()) {
      return pattern.equals(actual);
    }
    for (int index = 0; index < expected.arguments().size(); index++) {
      if (!matchCoreTemplate(
          expected.arguments().get(index), found.arguments().get(index), substitutions)) {
        return false;
      }
    }
    return true;
  }

  private static CoreType interfaceType(DefinitionId definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        arguments,
        CoreValueCategory.POLYMORPHIC,
        CoreNullability.NON_NULL);
  }

  private static CoreType safeResult(CoreType result, boolean nullSafe, CoreType receiver) {
    return nullSafe && receiver.isNullable() ? result.asNullable() : result;
  }

  private static boolean mayContainNull(CoreType type) {
    return type.isNullable() || type instanceof CoreType.Parameter;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          declared.nullability() == CoreNullability.NON_NULL
              ? declared
              : new CoreType.Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          parameter.nullability() == CoreNullability.NON_NULL
              ? parameter
              : new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Special special -> special;
    };
  }
}
