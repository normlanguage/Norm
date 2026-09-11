package dev.w0fv1.norm.core;

import dev.w0fv1.norm.core.CoreVerificationTypes.CallableSignature;
import dev.w0fv1.norm.core.CoreVerificationTypes.InterfaceInstance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class CoreProgramVerifier {
  private final CoreProgram program;
  private final CoreVerificationTypes validationTypes;
  private final CoreIntrinsicVerifier intrinsicVerifier;
  private CoreFieldInterceptorProtocol fieldInterceptor;

  private CoreProgramVerifier(CoreProgram program) {
    this.program = Objects.requireNonNull(program, "program");
    validationTypes = new CoreVerificationTypes(program);
    intrinsicVerifier = new CoreIntrinsicVerifier(program, validationTypes);
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
    var functionInterceptor = CoreFunctionInterceptorProtocol.resolve(program).orElse(null);
    var parameterInterceptor = CoreParameterInterceptorProtocol.resolve(program).orElse(null);
    fieldInterceptor = CoreFieldInterceptorProtocol.resolve(program).orElse(null);
    for (CoreDefinitionRecord record : program.definitions()) {
      switch (record.definition()) {
        case CoreDefinition.Callable callable ->
            new CoreCallableVerifier(
                    program,
                    validationTypes,
                    intrinsicVerifier,
                    functionInterceptor,
                    parameterInterceptor)
                .verifyCallable(record.id(), callable);
        case CoreDefinition.Aggregate declaration -> verifyAggregate(record.id(), declaration);
        case CoreDefinition.Enum declaration -> verifyEnum(record.id(), declaration);
        case CoreDefinition.Interface ignored -> {}
        case CoreDefinition.MethodSignature method -> verifyMethodSignature(record.id(), method);
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
          (CoreType.Declared)
              validationTypes.absolute(record.id(), conformance.concreteBuiltinType());
      BuiltinTypeId builtin = ((CoreTypeConstructor.Builtin) concrete.constructor()).id();
      InterfaceInstance instance =
          validationTypes.interfaceInstance(record.id(), conformance.interfaceType());
      CoreType.Declared previous =
          interfaces
              .computeIfAbsent(builtin, ignored -> new LinkedHashMap<>())
              .putIfAbsent(instance.definition(), instance.type());
      if (previous != null && !previous.equals(instance.type())) {
        throw new IllegalArgumentException("builtin interface instantiations conflict");
      }
      Set<DefinitionId> requirements = indexed.computeIfAbsent(builtin, ignored -> new HashSet<>());
      for (CoreWitness witness : conformance.witnesses()) {
        if (!requirements.add(validationTypes.resolve(record.id(), witness.requirement()))) {
          throw new IllegalArgumentException("builtin interface dispatch must be unique");
        }
      }
    }
  }

  private void verifyAggregate(DefinitionId id, CoreDefinition.Aggregate declaration) {
    if (declaration.kind()
        == CoreAggregateKind.VALUE
        != (declaration.valueCategory() == CoreValueCategory.VALUE)) {
      throw new IllegalArgumentException("aggregate kind and value category disagree");
    }
    if (declaration.kind() == CoreAggregateKind.ANNOTATION) {
      if (!declaration.typeParameters().isEmpty() || declaration.parentType().isPresent()) {
        throw new IllegalArgumentException("annotation cannot be generic or inherit a class");
      }
      CoreAnnotationPolicy.resolve(program, id, declaration);
    } else if (CoreAnnotationPolicy.usesPolicyInterfaces(program, id, declaration)) {
      throw new IllegalArgumentException(
          "annotation policy interfaces require an annotation aggregate");
    }
    int inheritedFields = 0;
    DefinitionId parentId = null;
    CoreType.Declared parentInstance = null;
    if (declaration.parentType().isPresent()) {
      CoreType parentType = declaration.parentType().orElseThrow();
      validationTypes.verifyValueType(id, parentType, declaration.typeParameters().size());
      CoreType absoluteParent = validationTypes.absolute(id, parentType);
      if (!(absoluteParent instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(program
                  .definition(CoreVerificationTypes.resolveExternal(user.definition()))
                  .orElseThrow()
              instanceof CoreDefinition.Aggregate parent)
          || declaration.kind() != CoreAggregateKind.CLASS
          || parent.kind() != CoreAggregateKind.CLASS
          || parent.valueCategory() != CoreValueCategory.IDENTITY
          || declaration.valueCategory() != CoreValueCategory.IDENTITY) {
        throw new IllegalArgumentException("aggregate inheritance requires class types");
      }
      parentInstance = declared;
      parentId = CoreVerificationTypes.resolveExternal(user.definition());
      inheritedFields = parent.fieldCount();
      validationTypes.requireAcyclicAggregate(id, parentId, new HashSet<>());
    }
    if (declaration.fieldCount() != inheritedFields + declaration.fields().size()) {
      throw new IllegalArgumentException("aggregate field count does not match its parent");
    }
    if (declaration.fields().stream().map(CoreField::name).distinct().count()
        != declaration.fields().size()) {
      throw new IllegalArgumentException("aggregate field names must be unique");
    }
    for (int index = 0; index < declaration.fields().size(); index++) {
      CoreField field = declaration.fields().get(index);
      if (field.ordinal() != inheritedFields + index) {
        throw new IllegalArgumentException("aggregate field ordinals must be contiguous");
      }
      validationTypes.verifyStoredType(id, field.type(), declaration.typeParameters().size());
      Set<DefinitionId> interceptors = new HashSet<>();
      for (CoreInterceptor interceptor : field.interceptors()) {
        DefinitionId annotation = validationTypes.resolve(id, interceptor.annotation());
        if (!interceptors.add(annotation)) {
          throw new IllegalArgumentException("field interceptors must be unique");
        }
        CoreAnnotationVerifier.verifyFieldInterceptor(
            program, id, field, interceptor, fieldInterceptor);
      }
    }
    validationTypes.verifyTypeParameters(
        id, declaration.typeParameters(), declaration.typeParameters().size());
    validationTypes.verifyExceptionDescendant(id, declaration);
    Set<DefinitionId> constructors = new HashSet<>();
    for (CoreDefinitionLink constructorReference : declaration.constructors()) {
      DefinitionId constructorId = validationTypes.resolve(id, constructorReference);
      CoreDefinition constructor = program.definition(constructorId).orElseThrow();
      if (!constructors.add(constructorId)
          || !(constructor instanceof CoreDefinition.Callable constructorCallable)
          || constructorCallable.receiverType().isEmpty()
          || !constructorCallable.typeParameters().isEmpty()
          || !constructorCallable.returnType().equals(CoreType.VOID)
          || !validationTypes.isReceiverOf(
              constructorId,
              constructorCallable.receiverType().orElseThrow(),
              id,
              declaration.typeParameters().size())) {
        throw new IllegalArgumentException("aggregate constructor must target a distinct method");
      }
    }
    if (declaration.kind() == CoreAggregateKind.ANNOTATION) {
      CoreAnnotationVerifier.verifyDeclaration(program, id, declaration);
    }
    verifyAggregateDispatch(id, declaration, parentId, parentInstance);
    Set<DefinitionId> interfaces = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inheritedInterfaces = new LinkedHashMap<>();
    for (CoreConformance conformance : declaration.conformances()) {
      validationTypes.verifyValueType(
          id, conformance.interfaceType(), declaration.typeParameters().size());
      InterfaceInstance instance =
          validationTypes.interfaceInstance(id, conformance.interfaceType());
      if (!interfaces.add(instance.definition())) {
        throw new IllegalArgumentException("aggregate conformances must be unique");
      }
      validationTypes.collectInterfaceInstances(instance, inheritedInterfaces);
      verifyConformance(id, declaration, instance, conformance);
    }
  }

  private void verifyAggregateDispatch(
      DefinitionId id,
      CoreDefinition.Aggregate declaration,
      DefinitionId parentId,
      CoreType.Declared parentInstance) {
    CoreType.Declared self = CoreVerificationTypes.aggregateType(id, declaration);
    Map<DefinitionId, CoreMethodDispatch> dispatchBySlot = new LinkedHashMap<>();
    for (CoreMethodDispatch dispatch : declaration.dispatch()) {
      DefinitionId slotId = validationTypes.resolve(id, dispatch.slot());
      if (dispatchBySlot.putIfAbsent(slotId, dispatch) != null) {
        throw new IllegalArgumentException("aggregate method dispatch slots must be unique");
      }
      DefinitionId implementationId = validationTypes.resolve(id, dispatch.target());
      CallableSignature slot = dispatchMethod(slotId, "dispatch slot");
      CallableSignature implementation =
          dispatchMethod(implementationId, "dispatch implementation");
      if (!implementation.implemented() && declaration.kind() != CoreAggregateKind.CLASS) {
        throw new IllegalArgumentException(
            "only class dispatch may target a managed method signature");
      }
      validationTypes.verifyValueType(
          id, dispatch.receiverType(), declaration.typeParameters().size());
      CoreType receiverType = validationTypes.absolute(id, dispatch.receiverType());
      DefinitionId implementationOwner =
          validationTypes.methodOwner(implementationId, implementation);
      CoreType.Declared implementationView =
          validationTypes.aggregateView(self, implementationOwner);
      if (implementationView == null || !implementationView.equals(receiverType)) {
        throw new IllegalArgumentException(
            "aggregate dispatch receiver does not match its implementation owner");
      }
      CoreType.Declared slotView =
          validationTypes.aggregateView(self, validationTypes.methodOwner(slotId, slot));
      if (slotView == null) {
        throw new IllegalArgumentException("aggregate dispatch slot is not inherited");
      }
      verifyCallableSignatureAbi(
          slotId,
          slot,
          slotView,
          implementationId,
          implementation,
          implementationView,
          declaration.typeParameters().size());
    }

    Map<DefinitionId, CoreMethodDispatch> inherited = new LinkedHashMap<>();
    if (parentId != null) {
      Map<DefinitionId, DefinitionId> replacements = new LinkedHashMap<>();
      CoreDefinition.Aggregate parent =
          (CoreDefinition.Aggregate) program.definition(parentId).orElseThrow();
      for (CoreMethodDispatch parentDispatch : parent.dispatch()) {
        DefinitionId slotId = validationTypes.resolve(parentId, parentDispatch.slot());
        inherited.put(slotId, parentDispatch);
        CoreMethodDispatch actual = dispatchBySlot.get(slotId);
        if (actual == null) {
          throw new IllegalArgumentException("aggregate dispatch omits an inherited slot");
        }
        DefinitionId parentImplementation =
            validationTypes.resolve(parentId, parentDispatch.target());
        DefinitionId actualImplementation = validationTypes.resolve(id, actual.target());
        DefinitionId previous =
            replacements.putIfAbsent(parentImplementation, actualImplementation);
        if (previous != null && !previous.equals(actualImplementation)) {
          throw new IllegalArgumentException(
              "aggregate dispatch must replace an inherited implementation consistently");
        }
        if (actualImplementation.equals(parentImplementation)) {
          CoreType expectedReceiver =
              validationTypes
                  .absolute(parentId, parentDispatch.receiverType())
                  .substitute(parentInstance.arguments()::get);
          CoreVerificationTypes.requireSameAbsoluteType(
              expectedReceiver,
              validationTypes.absolute(id, actual.receiverType()),
              "inherited dispatch receiver");
        } else {
          if (!validationTypes
              .methodOwner(
                  actualImplementation,
                  dispatchMethod(actualImplementation, "dispatch implementation"))
              .equals(id)) {
            throw new IllegalArgumentException(
                "aggregate dispatch override must be declared by the child");
          }
          CoreMethodDispatch selfSlot = dispatchBySlot.get(actualImplementation);
          if (selfSlot == null
              || !validationTypes.resolve(id, selfSlot.target()).equals(actualImplementation)) {
            throw new IllegalArgumentException(
                "aggregate dispatch override must declare its own slot");
          }
        }
      }
    }
    for (Map.Entry<DefinitionId, CoreMethodDispatch> entry : dispatchBySlot.entrySet()) {
      if (inherited.containsKey(entry.getKey())) continue;
      DefinitionId implementation = validationTypes.resolve(id, entry.getValue().target());
      if (!validationTypes
              .methodOwner(entry.getKey(), dispatchMethod(entry.getKey(), "dispatch slot"))
              .equals(id)
          || !implementation.equals(entry.getKey())
          || !validationTypes.absolute(id, entry.getValue().receiverType()).equals(self)) {
        throw new IllegalArgumentException(
            "new aggregate dispatch slots must be declared by the aggregate");
      }
    }
  }

  private CallableSignature dispatchMethod(DefinitionId id, String subject) {
    CallableSignature method = validationTypes.callableSignature(id, subject);
    if (!method.hasReceiver()
        || !method.captureTypes().isEmpty()
        || validationTypes.isAggregateConstructor(id)) {
      throw new IllegalArgumentException(subject + " must be a non-capturing method");
    }
    return method;
  }

  private void verifyCallableSignatureAbi(
      DefinitionId slotId,
      CallableSignature slot,
      CoreType.Declared slotView,
      DefinitionId implementationId,
      CallableSignature implementation,
      CoreType.Declared implementationView,
      int aggregateParameterCount) {
    if (slot.typeParameters().size() != implementation.typeParameters().size()
        || slot.parameterTypes().size() != implementation.parameterTypes().size()) {
      throw new IllegalArgumentException("dispatch implementation does not match its slot ABI");
    }
    for (int index = 0; index < slot.typeParameters().size(); index++) {
      Optional<CoreType> expected =
          normalizedMethodBound(
              slotId, slot, slotView, slot.typeParameters().get(index), aggregateParameterCount);
      Optional<CoreType> actual =
          normalizedMethodBound(
              implementationId,
              implementation,
              implementationView,
              implementation.typeParameters().get(index),
              aggregateParameterCount);
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("dispatch implementation does not match its slot ABI");
      }
    }
    for (int index = 0; index < slot.parameterTypes().size(); index++) {
      CoreVerificationTypes.requireSameAbsoluteType(
          normalizeMethodType(
              slotId, slot.parameterTypes().get(index), slot, slotView, aggregateParameterCount),
          normalizeMethodType(
              implementationId,
              implementation.parameterTypes().get(index),
              implementation,
              implementationView,
              aggregateParameterCount),
          "dispatch parameter");
    }
    CoreType expectedResult =
        normalizeMethodType(slotId, slot.returnType(), slot, slotView, aggregateParameterCount);
    CoreType actualResult =
        normalizeMethodType(
            implementationId,
            implementation.returnType(),
            implementation,
            implementationView,
            aggregateParameterCount);
    validationTypes.requireAssignable(expectedResult, actualResult, "dispatch result");
  }

  private Optional<CoreType> normalizedMethodBound(
      DefinitionId owner,
      CallableSignature method,
      CoreType.Declared receiver,
      CoreTypeParameter parameter,
      int aggregateParameterCount) {
    return parameter
        .upperBound()
        .map(bound -> normalizeMethodType(owner, bound, method, receiver, aggregateParameterCount));
  }

  private CoreType normalizeMethodType(
      DefinitionId owner,
      CoreType type,
      CallableSignature method,
      CoreType.Declared receiver,
      int aggregateParameterCount) {
    int receiverParameters = method.receiverTypeParameterCount();
    return validationTypes
        .absolute(owner, type)
        .substitute(
            index -> {
              if (index < receiverParameters) return receiver.arguments().get(index);
              return new CoreType.Parameter(
                  aggregateParameterCount + index - receiverParameters, CoreNullability.NON_NULL);
            });
  }

  private void verifyInterface(DefinitionId id, CoreDefinition.Interface declaration) {
    validationTypes.verifyTypeParameters(
        id, declaration.typeParameters(), declaration.typeParameters().size());
    Set<DefinitionId> parents = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inherited = new LinkedHashMap<>();
    for (CoreType parent : declaration.directParents()) {
      validationTypes.verifyValueType(id, parent, declaration.typeParameters().size());
      InterfaceInstance instance = validationTypes.interfaceInstance(id, parent);
      if (!parents.add(instance.definition())) {
        throw new IllegalArgumentException("direct interface parents must be unique");
      }
      validationTypes.collectInterfaceInstances(instance, inherited);
      validationTypes.requireAcyclicInterface(id, instance.definition(), new HashSet<>());
    }
    Set<DefinitionId> methods = new HashSet<>();
    for (CoreDefinitionLink link : declaration.declaredMethods()) {
      DefinitionId methodId = validationTypes.resolve(id, link);
      if (!methods.add(methodId)) {
        throw new IllegalArgumentException("declared interface methods must be unique");
      }
      CoreDefinition target = program.definition(methodId).orElseThrow();
      if (!(target instanceof CoreDefinition.MethodSignature method)
          || !validationTypes.isReceiverOf(
              methodId, method.receiverType(), id, declaration.typeParameters().size())) {
        throw new IllegalArgumentException("declared interface method has the wrong receiver ABI");
      }
    }
  }

  private void verifyMethodSignature(DefinitionId id, CoreDefinition.MethodSignature method) {
    CoreType receiver = validationTypes.absolute(id, method.receiverType());
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("method signature receiver must be a nominal type");
    }
    DefinitionId receiverId = CoreVerificationTypes.resolveExternal(user.definition());
    CoreDefinition receiverDefinition = program.definition(receiverId).orElseThrow();
    int receiverParameterCount =
        switch (receiverDefinition) {
          case CoreDefinition.Aggregate aggregate -> aggregate.typeParameters().size();
          case CoreDefinition.Interface contract -> contract.typeParameters().size();
          default ->
              throw new IllegalArgumentException(
                  "method signature receiver must be a class, value, or interface");
        };
    int parameterCount = receiverParameterCount + method.typeParameters().size();
    validationTypes.verifyValueType(id, method.receiverType(), parameterCount);
    if (!validationTypes.isReceiverOf(
        id, method.receiverType(), receiverId, receiverParameterCount)) {
      throw new IllegalArgumentException(
          "method signature receiver must expose its type parameters");
    }
    validationTypes.verifyTypeParameters(id, method.typeParameters(), parameterCount);
    method
        .parameterTypes()
        .forEach(type -> validationTypes.verifyParameterType(id, type, parameterCount));
    validationTypes.verifyReturnType(id, method.returnType(), parameterCount);
  }

  private void verifyConformance(
      DefinitionId aggregateId,
      CoreDefinition.Aggregate declaration,
      InterfaceInstance instance,
      CoreConformance conformance) {
    Map<DefinitionId, CoreDefinition.MethodSignature> requirements =
        validationTypes.inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirementId = validationTypes.resolve(aggregateId, witness.requirement());
      if (witnesses.putIfAbsent(requirementId, witness.implementation()) != null) {
        throw new IllegalArgumentException("conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.MethodSignature> entry : requirements.entrySet()) {
      DefinitionId requirementId = entry.getKey();
      CoreDefinition.MethodSignature requirement = entry.getValue();
      CoreWitnessTarget witness = witnesses.get(requirementId);
      if (!(witness instanceof CoreWitnessTarget.Callable callableWitness)) {
        throw new IllegalArgumentException("source aggregate witnesses must target callables");
      }
      DefinitionId implementationId =
          validationTypes.resolve(aggregateId, callableWitness.definition());
      CoreDefinition implementationDefinition = program.definition(implementationId).orElseThrow();
      boolean aggregateReceiver =
          implementationDefinition instanceof CoreDefinition.Callable candidate
              && candidate.receiverType().isPresent()
              && validationTypes.isReceiverOf(
                  implementationId,
                  candidate.receiverType().orElseThrow(),
                  aggregateId,
                  declaration.typeParameters().size());
      boolean defaultReceiver =
          implementationDefinition instanceof CoreDefinition.Callable candidate
              && candidate.receiverType().isPresent()
              && validationTypes.absolute(implementationId, candidate.receiverType().orElseThrow())
                  instanceof CoreType.Declared receiver
              && receiver.constructor() instanceof CoreTypeConstructor.User user
              && validationTypes.interfaceSubstitutions(
                      instance, CoreVerificationTypes.resolveExternal(user.definition()))
                  != null;
      int expectedReified =
          aggregateReceiver
              ? declaration.typeParameters().size() + requirement.typeParameters().size()
              : implementationDefinition instanceof CoreDefinition.Callable candidate
                  ? candidate.receiverTypeParameterCount() + candidate.typeParameters().size()
                  : -1;
      if (!(implementationDefinition instanceof CoreDefinition.Callable implementation)
          || implementation.receiverType().isEmpty()
          || implementation.reifiedTypeLocals().size() != expectedReified
          || (!aggregateReceiver && !defaultReceiver)) {
        throw new IllegalArgumentException(
            "conformance witness implementation has an incompatible receiver");
      }
      InterfaceInstance requirementOwner =
          validationTypes.interfaceInstance(requirementId, requirement.receiverType());
      List<CoreType> substitutions =
          validationTypes.interfaceSubstitutions(instance, requirementOwner.definition());
      if (substitutions != null) {
        substitutions = new ArrayList<>(substitutions);
        for (int index = 0; index < requirement.typeParameters().size(); index++) {
          substitutions.add(
              new CoreType.Parameter(
                  declaration.typeParameters().size() + index, CoreNullability.NON_NULL));
        }
      }
      List<CoreType> implementationSubstitutions = new ArrayList<>();
      if (aggregateReceiver) {
        for (int index = 0; index < declaration.typeParameters().size(); index++) {
          implementationSubstitutions.add(new CoreType.Parameter(index, CoreNullability.NON_NULL));
        }
      } else {
        InterfaceInstance implementationOwner =
            validationTypes.interfaceInstance(
                implementationId, implementation.receiverType().orElseThrow());
        List<CoreType> receiverSubstitutions =
            validationTypes.interfaceSubstitutions(instance, implementationOwner.definition());
        if (receiverSubstitutions != null) {
          implementationSubstitutions.addAll(receiverSubstitutions);
        }
      }
      for (int index = 0; index < requirement.typeParameters().size(); index++) {
        implementationSubstitutions.add(
            new CoreType.Parameter(
                declaration.typeParameters().size() + index, CoreNullability.NON_NULL));
      }
      if (substitutions == null
          || implementationSubstitutions.size() != implementation.reifiedTypeLocals().size()
          || implementation.parameterTypes().size() != requirement.parameterTypes().size()) {
        throw new IllegalArgumentException(
            "conformance witness ABI does not match its requirement");
      }
      for (int parameter = 0; parameter < requirement.parameterTypes().size(); parameter++) {
        CoreType expected =
            validationTypes
                .absolute(requirementId, requirement.parameterTypes().get(parameter))
                .substitute(substitutions::get);
        CoreVerificationTypes.requireSameAbsoluteType(
            expected,
            validationTypes
                .absolute(implementationId, implementation.parameterTypes().get(parameter))
                .substitute(implementationSubstitutions::get),
            "conformance witness parameter");
      }
      CoreType expectedReturn =
          validationTypes
              .absolute(requirementId, requirement.returnType())
              .substitute(substitutions::get);
      validationTypes.requireAssignable(
          expectedReturn,
          validationTypes
              .absolute(implementationId, implementation.returnType())
              .substitute(implementationSubstitutions::get),
          "conformance witness return");
      for (int index = 0; index < requirement.typeParameters().size(); index++) {
        CoreTypeParameter requiredParameter = requirement.typeParameters().get(index);
        CoreTypeParameter implementationParameter = implementation.typeParameters().get(index);
        if (requiredParameter.upperBound().isPresent()
            != implementationParameter.upperBound().isPresent()) {
          throw new IllegalArgumentException("conformance witness generic bound does not match");
        }
        if (requiredParameter.defaultType().isPresent()
            != implementationParameter.defaultType().isPresent()) {
          throw new IllegalArgumentException("conformance witness generic default does not match");
        }
        if (requiredParameter.upperBound().isPresent()) {
          CoreType expectedBound =
              validationTypes
                  .absolute(requirementId, requiredParameter.upperBound().orElseThrow())
                  .substitute(substitutions::get);
          CoreVerificationTypes.requireSameAbsoluteType(
              expectedBound,
              validationTypes
                  .absolute(implementationId, implementationParameter.upperBound().orElseThrow())
                  .substitute(implementationSubstitutions::get),
              "conformance witness generic bound");
        }
        if (requiredParameter.defaultType().isPresent()) {
          CoreType expectedDefault =
              validationTypes
                  .absolute(requirementId, requiredParameter.defaultType().orElseThrow())
                  .substitute(substitutions::get);
          CoreVerificationTypes.requireSameAbsoluteType(
              expectedDefault,
              validationTypes
                  .absolute(implementationId, implementationParameter.defaultType().orElseThrow())
                  .substitute(implementationSubstitutions::get),
              "conformance witness generic default");
        }
      }
    }
  }

  private void verifyBuiltinConformance(
      DefinitionId owner, CoreDefinition.BuiltinConformance conformance) {
    int parameterCount = conformance.typeParameters().size();
    validationTypes.verifyTypeParameters(owner, conformance.typeParameters(), parameterCount);
    validationTypes.verifyValueType(owner, conformance.concreteBuiltinType(), parameterCount);
    CoreType concrete = validationTypes.absolute(owner, conformance.concreteBuiltinType());
    if (!(concrete instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin)) {
      throw new IllegalArgumentException("builtin conformance requires a builtin concrete type");
    }
    validationTypes.verifyValueType(owner, conformance.interfaceType(), parameterCount);
    InterfaceInstance instance =
        validationTypes.interfaceInstance(owner, conformance.interfaceType());
    Map<DefinitionId, CoreDefinition.MethodSignature> requirements =
        validationTypes.inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirement = validationTypes.resolve(owner, witness.requirement());
      if (witnesses.putIfAbsent(requirement, witness.implementation()) != null) {
        throw new IllegalArgumentException("builtin conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("builtin conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.MethodSignature> entry : requirements.entrySet()) {
      CoreWitnessTarget witness = witnesses.get(entry.getKey());
      boolean valid =
          switch (witness) {
            case CoreWitnessTarget.Intrinsic intrinsic ->
                intrinsicVerifier.matchesIntrinsicWitness(
                    owner,
                    concrete,
                    instance,
                    entry.getKey(),
                    entry.getValue(),
                    intrinsic.intrinsic());
            case CoreWitnessTarget.Callable callable ->
                matchesDefaultWitness(
                    owner, conformance, instance, entry.getKey(), entry.getValue(), callable);
          };
      if (!valid) {
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

  private boolean matchesDefaultWitness(
      DefinitionId owner,
      CoreDefinition.BuiltinConformance conformance,
      InterfaceInstance instance,
      DefinitionId requirementId,
      CoreDefinition.MethodSignature requirement,
      CoreWitnessTarget.Callable witness) {
    DefinitionId implementationId = validationTypes.resolve(owner, witness.definition());
    CoreDefinition definition = program.definition(implementationId).orElse(null);
    if (!(definition instanceof CoreDefinition.Callable implementation)
        || implementation.receiverType().isEmpty()
        || implementation.reifiedTypeLocals().size()
            != ((CoreType.Declared)
                        validationTypes.absolute(requirementId, requirement.receiverType()))
                    .arguments()
                    .size()
                + requirement.typeParameters().size()
        || !validationTypes
            .absolute(implementationId, implementation.receiverType().orElseThrow())
            .equals(validationTypes.absolute(requirementId, requirement.receiverType()))) {
      return false;
    }
    if (implementation.parameterTypes().size() != requirement.parameterTypes().size()) {
      return false;
    }
    for (int index = 0; index < requirement.parameterTypes().size(); index++) {
      CoreType expected =
          validationTypes.absolute(requirementId, requirement.parameterTypes().get(index));
      if (!expected.equals(
          validationTypes.absolute(implementationId, implementation.parameterTypes().get(index)))) {
        return false;
      }
    }
    CoreType expectedReturn = validationTypes.absolute(requirementId, requirement.returnType());
    return expectedReturn.equals(
        validationTypes.absolute(implementationId, implementation.returnType()));
  }

  private void verifyEnum(DefinitionId id, CoreDefinition.Enum declaration) {
    validationTypes.verifyTypeParameters(
        id, declaration.typeParameters(), declaration.typeParameters().size());
    if (declaration.variants().stream().map(CoreEnumVariant::key).distinct().count()
        != declaration.variants().size()) {
      throw new IllegalArgumentException("enum variants must be unique");
    }
    declaration
        .variants()
        .forEach(
            variant -> {
              if (variant.fields().stream().map(CoreField::name).distinct().count()
                  != variant.fields().size()) {
                throw new IllegalArgumentException("enum field names must be unique");
              }
              for (int index = 0; index < variant.fields().size(); index++) {
                CoreField field = variant.fields().get(index);
                if (field.ordinal() != index
                    || field.visibility() != CoreVisibility.PUBLIC
                    || !field.interceptors().isEmpty()) {
                  throw new IllegalArgumentException("enum field structure is invalid");
                }
                validationTypes.verifyStoredType(
                    id, field.type(), declaration.typeParameters().size());
              }
            });
  }
}
