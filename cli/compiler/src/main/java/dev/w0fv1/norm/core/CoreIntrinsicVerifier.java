package dev.w0fv1.norm.core;

import dev.w0fv1.norm.abi.AbiType;
import dev.w0fv1.norm.abi.BuiltinContracts;
import dev.w0fv1.norm.core.CoreVerificationTypes.InterfaceInstance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CoreIntrinsicVerifier {
  private final CoreProgram program;
  private final CoreVerificationTypes validationTypes;
  private final BuiltinContracts builtins = BuiltinContracts.standard();

  CoreIntrinsicVerifier(CoreProgram program, CoreVerificationTypes validationTypes) {
    this.program = program;
    this.validationTypes = validationTypes;
  }

  boolean matchesIntrinsicWitness(
      DefinitionId owner,
      CoreType concrete,
      InterfaceInstance instance,
      DefinitionId requirementId,
      CoreDefinition.MethodSignature requirement,
      dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
    if (!requirement.typeParameters().isEmpty()) return false;
    InterfaceInstance requirementOwner =
        validationTypes.interfaceInstance(requirementId, requirement.receiverType());
    List<CoreType> interfaceArguments =
        validationTypes.interfaceSubstitutions(instance, requirementOwner.definition());
    if (interfaceArguments == null) return false;
    for (BuiltinContracts.IntrinsicCandidate candidate : builtins.intrinsicCandidates(intrinsic)) {
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
            validationTypes
                .absolute(requirementId, requirement.parameterTypes().get(index))
                .substitute(interfaceArguments::get);
        if (!matchesAbiType(expected, candidate.parameters().get(index).type(), substitutions)) {
          parametersMatch = false;
          break;
        }
      }
      CoreType expectedReturn =
          validationTypes
              .absolute(requirementId, requirement.returnType())
              .substitute(interfaceArguments::get);
      if (parametersMatch && matchesAbiType(expectedReturn, candidate.result(), substitutions)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesAbiType(
      CoreType expected, AbiType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType substituted = substitutions.get(pattern.identity());
        yield substituted != null
            && expected.equals(pattern.isNullable() ? substituted.asNullable() : substituted);
      }
      case VOID -> expected.equals(CoreType.VOID);
      case NULL -> expected.equals(CoreType.NULL);
      case ERROR -> true;
      case EXISTENTIAL -> true;
      case REFERENCE ->
          expected instanceof CoreType.Reference reference
              && matchesAbiType(reference.target(), pattern.referenceTarget(), substitutions);
      case DECLARED -> {
        if (!(expected instanceof CoreType.Declared declared)
            || declared.arguments().size() != pattern.arguments().size()
            || declared.category() != category(pattern.category())
            || declared.isNullable() != pattern.isNullable()
            || !matchesAbiConstructor(declared.constructor(), pattern.identity())) {
          yield false;
        }
        boolean matches = true;
        for (int index = 0; index < pattern.arguments().size(); index++) {
          if (!matchesAbiType(
              declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
            matches = false;
            break;
          }
        }
        yield matches;
      }
    };
  }

  private boolean matchesAbiConstructor(CoreTypeConstructor constructor, String abiIdentity) {
    return switch (constructor) {
      case CoreTypeConstructor.Builtin builtin -> builtin.id().value().equals(abiIdentity);
      case CoreTypeConstructor.User user -> {
        DefinitionId id = CoreVerificationTypes.resolveExternal(user.definition());
        CoreDefinition definition = program.definition(id).orElseThrow();
        CoreNominalTypeKey nominal =
            switch (definition) {
              case CoreDefinition.Aggregate declaration -> declaration.nominalType();
              case CoreDefinition.Enum declaration -> declaration.nominalType();
              case CoreDefinition.Interface declaration -> declaration.nominalType();
              default -> null;
            };
        yield nominal != null
            && nominal.module().name().equals("std")
            && (nominal.packageName() + "." + nominal.name()).equals(abiIdentity);
      }
    };
  }

  void verifyIndex(DefinitionId owner, CoreExpression.Index index) {
    validationTypes.requireNonNullableReceiver(owner, index.receiver().type(), "index read");
    boolean valid =
        builtins.indexCandidates(index.readIntrinsic()).stream()
            .anyMatch(candidate -> matchesIndex(owner, index, candidate));
    if (!valid)
      throw new IllegalArgumentException("index expression does not match its builtin ABI");
  }

  boolean matchesCollectionLiteral(
      DefinitionId owner, CoreExpression.CollectionLiteral collection) {
    CoreType actual = validationTypes.absolute(owner, collection.type());
    for (BuiltinContracts.IntrinsicCandidate candidate :
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
      DefinitionId owner, CoreExpression.Index index, BuiltinContracts.IndexCandidate candidate) {
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, index.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (!candidate.writeIntrinsic().equals(index.writeIntrinsic())) return false;
    CoreType expectedIndex = instantiate(candidate.index(), substitutions);
    CoreType expectedResult = instantiate(candidate.result(), substitutions);
    return validationTypes.isAssignable(
            expectedIndex, validationTypes.absolute(owner, index.index().type()))
        && expectedResult.equals(validationTypes.absolute(owner, index.type()));
  }

  void verifyIntrinsic(DefinitionId owner, CoreExpression.Intrinsic intrinsic) {
    boolean valid =
        builtins.intrinsicCandidates(intrinsic.intrinsic()).stream()
            .anyMatch(candidate -> matchesIntrinsic(owner, intrinsic, candidate));
    if (!valid) {
      throw new IllegalArgumentException(
          "intrinsic expression does not match its builtin ABI: " + intrinsic.intrinsic());
    }
    if (intrinsic.intrinsic() == dev.w0fv1.norm.abi.IntrinsicId.CLASS_ANNOTATION
        || intrinsic.intrinsic() == dev.w0fv1.norm.abi.IntrinsicId.FIELD_ANNOTATION) {
      CoreType annotationType =
          CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, intrinsic.type()));
      if (!(annotationType instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(program.definition(validationTypes.resolve(owner, user.definition())).orElse(null)
              instanceof CoreDefinition.Aggregate annotation)
          || annotation.kind() != CoreAggregateKind.ANNOTATION) {
        throw new IllegalArgumentException("annotation query result must name an annotation");
      }
    }
  }

  private boolean matchesIntrinsic(
      DefinitionId owner,
      CoreExpression.Intrinsic intrinsic,
      BuiltinContracts.IntrinsicCandidate candidate) {
    if (candidate.receiver().isPresent() != intrinsic.receiver().isPresent()
        || candidate.runtimeType() != intrinsic.runtimeType().isPresent()
        || intrinsic.nullSafe() && intrinsic.receiver().isEmpty()
        || !CoreVerificationTypes.denseArguments(
            intrinsic.arguments(), candidate.parameters().size())) {
      return false;
    }
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (candidate.receiver().isPresent()) {
      CoreType actualReceiver =
          validationTypes.absolute(owner, intrinsic.receiver().orElseThrow().type());
      if (!intrinsic.nullSafe() && actualReceiver.isNullable()) return false;
      if (!bindPattern(
          CoreVerificationTypes.nonNullable(actualReceiver),
          candidate.receiver().orElseThrow(),
          substitutions)) {
        return false;
      }
    }
    if (candidate.runtimeType()) {
      CoreType runtimeTemplate =
          validationTypes.absolute(owner, intrinsic.runtimeType().orElseThrow().template());
      if (!bindPattern(runtimeTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      AbiType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType actual = validationTypes.absolute(owner, argument.value().type());
      Map<String, CoreType> argumentBindings = new LinkedHashMap<>(substitutions);
      if (bindPattern(actual, parameter, argumentBindings)) {
        substitutions.putAll(argumentBindings);
      }
    }
    if (!candidate.runtimeType() && containsUnbound(candidate.result(), substitutions)) {
      CoreType resultTemplate = validationTypes.absolute(owner, intrinsic.type());
      if (intrinsic.nullSafe()) resultTemplate = CoreVerificationTypes.nonNullable(resultTemplate);
      if (!bindPattern(resultTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      AbiType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType actual = validationTypes.absolute(owner, argument.value().type());
      if (containsUnbound(parameter, substitutions)) return false;
      CoreType expected = instantiate(parameter, substitutions);
      if (!expected.equals(CoreType.DYNAMIC) && !validationTypes.isAssignable(expected, actual)) {
        return false;
      }
    }
    if (containsUnbound(candidate.result(), substitutions)) return false;
    CoreType result = instantiate(candidate.result(), substitutions);
    CoreType receiver =
        intrinsic
            .receiver()
            .map(CoreExpression::type)
            .map(type -> validationTypes.absolute(owner, type))
            .orElse(CoreType.DYNAMIC);
    result = CoreVerificationTypes.safeResult(result, intrinsic.nullSafe(), receiver);
    return result.equals(validationTypes.absolute(owner, intrinsic.type()));
  }

  void verifyIntrinsicAssignment(DefinitionId owner, CoreStatement.IntrinsicAssignment assignment) {
    validationTypes.requireNonNullableReceiver(
        owner, assignment.receiver().type(), "intrinsic assignment");
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
      BuiltinContracts.WriteCandidate candidate) {
    if (candidate.index().isPresent() != assignment.index().isPresent()) return false;
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        CoreVerificationTypes.nonNullable(
            validationTypes.absolute(owner, assignment.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (candidate.index().isPresent()) {
      CoreType expectedIndex = instantiate(candidate.index().orElseThrow(), substitutions);
      if (!validationTypes.isAssignable(
          expectedIndex,
          validationTypes.absolute(owner, assignment.index().orElseThrow().type()))) {
        return false;
      }
    }
    CoreType expectedValue = instantiate(candidate.value(), substitutions);
    return validationTypes.isAssignable(
        expectedValue, validationTypes.absolute(owner, assignment.value().type()));
  }

  void verifyIteration(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      int variableLocal,
      CoreExpression iterable,
      CoreIteration iteration) {
    validationTypes.requireNonNullableReceiver(owner, iterable.type(), "iteration");
    CoreType variable =
        validationTypes.absolute(
            owner, CoreVerificationTypes.local(callable, variableLocal).type());
    switch (iteration) {
      case CoreIteration.Builtin builtin -> {
        boolean valid =
            builtins.iterationCandidates(builtin.intrinsic()).stream()
                .anyMatch(
                    candidate -> {
                      Map<String, CoreType> substitutions = new LinkedHashMap<>();
                      return bindPattern(
                              CoreVerificationTypes.nonNullable(
                                  validationTypes.absolute(owner, iterable.type())),
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
                owner, callable, iterable.type(), protocol.iteratorRequirement());
        CoreType hasNext =
            verifyIterationRequirement(owner, callable, iterator, protocol.hasNextRequirement());
        CoreType next =
            verifyIterationRequirement(owner, callable, iterator, protocol.nextRequirement());
        CoreVerificationTypes.requireSameAbsoluteType(
            CoreType.BOOLEAN, hasNext, "iteration hasNext result");
        CoreVerificationTypes.requireSameAbsoluteType(variable, next, "iteration next result");
      }
    }
  }

  private CoreType verifyIterationRequirement(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreType receiverType,
      CoreDefinitionLink requirementLink) {
    DefinitionId requirementId = validationTypes.resolve(owner, requirementLink);
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.MethodSignature requirement)
        || !requirement.typeParameters().isEmpty()
        || !requirement.parameterTypes().isEmpty()) {
      throw new IllegalArgumentException("iteration requirement has the wrong callable ABI");
    }
    InterfaceInstance required =
        validationTypes.interfaceInstance(requirementId, requirement.receiverType());
    CoreType actualReceiver =
        CoreVerificationTypes.nonNullable(validationTypes.absolute(owner, receiverType));
    CoreType realized = validationTypes.realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          callable.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        realized =
            validationTypes.realizedInterface(
                CoreVerificationTypes.nonNullable(
                    validationTypes.absolute(owner, declaration.upperBound().orElseThrow())),
                required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    InterfaceInstance receiver = validationTypes.interfaceInstance(owner, realized);
    List<CoreType> substitutions =
        validationTypes.interfaceSubstitutions(receiver, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    return validationTypes
        .absolute(requirementId, requirement.returnType())
        .substitute(substitutions::get);
  }

  private boolean bindPattern(
      CoreType actual, AbiType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == AbiType.Kind.ERROR) return true;
    if (pattern.kind() == AbiType.Kind.EXISTENTIAL) return true;
    if (pattern.kind() == AbiType.Kind.TYPE_PARAMETER) {
      CoreType previous = substitutions.get(pattern.identity());
      if (previous != null) {
        CoreType expected = pattern.isNullable() ? previous.asNullable() : previous;
        return expected.equals(actual);
      }
      CoreType captured = pattern.isNullable() ? CoreVerificationTypes.nonNullable(actual) : actual;
      substitutions.put(pattern.identity(), captured);
      return true;
    }
    if (pattern.kind() == AbiType.Kind.VOID) return actual.equals(CoreType.VOID);
    if (pattern.kind() == AbiType.Kind.NULL) return actual.equals(CoreType.NULL);
    if (pattern.isFunction()) {
      if (pattern.isUnknownFunction()) {
        return actual instanceof CoreType.Function function
            && function.nullability()
                == (pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      }
      if (!(actual instanceof CoreType.Function function)
          || function.isNullable() != pattern.isNullable()
          || function.parameterTypes().size() != pattern.functionParameterTypes().size()
          || !bindPattern(function.returnType(), pattern.functionReturnType(), substitutions)) {
        return false;
      }
      for (int index = 0; index < function.parameterTypes().size(); index++) {
        if (!bindPattern(
            function.parameterTypes().get(index),
            pattern.functionParameterTypes().get(index),
            substitutions)) {
          return false;
        }
      }
      return true;
    }
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

  private static boolean containsUnbound(AbiType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == AbiType.Kind.TYPE_PARAMETER) {
      return !substitutions.containsKey(pattern.identity());
    }
    if (pattern.isFunction()) {
      if (pattern.isUnknownFunction()) return false;
      return containsUnbound(pattern.functionReturnType(), substitutions)
          || pattern.functionParameterTypes().stream()
              .anyMatch(parameter -> containsUnbound(parameter, substitutions));
    }
    return pattern.arguments().stream()
        .anyMatch(argument -> containsUnbound(argument, substitutions));
  }

  private CoreType instantiate(AbiType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType type = substitutions.get(pattern.identity());
        if (type == null)
          throw new IllegalArgumentException("builtin type parameter is unresolved");
        yield pattern.isNullable() ? type.asNullable() : type;
      }
      case DECLARED ->
          pattern.isFunction()
              ? new CoreType.Function(
                  pattern.isUnknownFunction()
                      ? CoreType.EXISTENTIAL
                      : instantiate(pattern.functionReturnType(), substitutions),
                  pattern.isUnknownFunction()
                      ? List.of()
                      : pattern.functionParameterTypes().stream()
                          .map(argument -> instantiate(argument, substitutions))
                          .toList(),
                  pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL)
              : new CoreType.Declared(
                  new CoreTypeConstructor.Builtin(new BuiltinTypeId(pattern.identity())),
                  pattern.arguments().stream()
                      .map(argument -> instantiate(argument, substitutions))
                      .toList(),
                  category(pattern.category()),
                  pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      case REFERENCE ->
          new CoreType.Reference(instantiate(pattern.referenceTarget(), substitutions));
      case VOID -> CoreType.VOID;
      case NULL -> CoreType.NULL;
      case ERROR -> CoreType.DYNAMIC;
      case EXISTENTIAL -> CoreType.EXISTENTIAL;
    };
  }

  static CoreValueCategory category(AbiType.Category category) {
    return switch (category) {
      case VALUE -> CoreValueCategory.VALUE;
      case IDENTITY -> CoreValueCategory.IDENTITY;
      case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
      case DYNAMIC -> CoreValueCategory.DYNAMIC;
      case VOID -> CoreValueCategory.VOID;
    };
  }
}
