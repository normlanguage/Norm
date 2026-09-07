package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.builtin.BuiltinSymbols;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.semantic.ArgumentBinding;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.TypeArguments;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CallResolver {
  private final DiagnosticBag diagnostics;
  private final SemanticModelBuilder model;
  private final BuiltinSymbols builtins;
  private final TypeResolutionState resolution;
  private final TypeSystem typeSystem;
  private final ExpressionTyping expressions;
  final CallArguments arguments;

  CallResolver(
      DiagnosticBag diagnostics,
      SemanticModelBuilder model,
      BuiltinSymbols builtins,
      TypeResolutionState resolution,
      TypeSystem typeSystem,
      ExpressionTyping expressions) {
    this.diagnostics = diagnostics;
    this.model = model;
    this.builtins = builtins;
    this.resolution = resolution;
    this.typeSystem = typeSystem;
    this.expressions = expressions;
    arguments = new CallArguments(diagnostics, INVALID_CALL);
  }

  CallResolution<Syntax.FunctionDecl> resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span) {
    return resolveSourceCall(
        candidates, explicitTypeArguments, call, expected, ownerSubstitutions, span, false);
  }

  CallResolution<Syntax.FunctionDecl> resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      boolean nullableAccess) {
    return resolveSourceCall(
        candidates,
        explicitTypeArguments,
        call,
        expected,
        ownerSubstitutions,
        span,
        null,
        nullableAccess);
  }

  CallResolution<Syntax.FunctionDecl> resolveExtensionCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Expression receiver,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span) {
    return resolveSourceCall(
        candidates, explicitTypeArguments, call, expected, Map.of(), span, receiver, false);
  }

  private CallResolution<Syntax.FunctionDecl> resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      Syntax.Expression extensionReceiver,
      boolean nullableAccess) {
    List<CallableTarget<Syntax.FunctionDecl>> signatures =
        candidates.stream()
            .map(
                declaration ->
                    new CallableTarget<>(
                        declaration,
                        declaration.name(),
                        model
                            .symbols()
                            .get(model.declarationSymbols().get(declaration))
                            .typeParameters(),
                        typeSystem.parametersOf(declaration, ownerSubstitutions),
                        typeSystem.functionReturnType(
                            declaration,
                            typeSystem.typeParameters(
                                declaration, typeSystem.ownerOf(declaration))),
                        ownerSubstitutions))
            .toList();
    return resolveCall(
        signatures, explicitTypeArguments, call, expected, span, extensionReceiver, nullableAccess);
  }

  CallResolution<InterfaceRequirement> resolveInterfaceCall(
      List<InterfaceRequirement> candidates,
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected,
      SemanticType nullableReceiver) {
    List<CallableTarget<InterfaceRequirement>> signatures =
        candidates.stream()
            .map(
                requirement ->
                    new CallableTarget<>(
                        requirement,
                        requirement.method().name(),
                        model
                            .symbols()
                            .get(model.declarationSymbols().get(requirement.method()))
                            .typeParameters(),
                        requirement.parameters(),
                        requirement.result(),
                        typeSystem.interfaceSubstitutions(
                            requirement.owner(), requirement.receiver())))
            .toList();
    return resolveCall(
        signatures,
        member.typeArguments(),
        call,
        expected,
        member.nameSpan(),
        null,
        member.nullSafe() && nullableReceiver.mayContainNull());
  }

  CallResolution<Symbol> resolveSymbolCall(
      List<Symbol> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span,
      boolean nullableAccess) {
    List<CallableTarget<Symbol>> signatures =
        candidates.stream()
            .map(
                symbol ->
                    new CallableTarget<>(
                        symbol,
                        symbol.name(),
                        symbol.typeParameters(),
                        symbol.parameters(),
                        symbol.type(),
                        Map.of()))
            .toList();
    return resolveCall(
        signatures, explicitTypeArguments, call, expected, span, null, nullableAccess);
  }

  private <T> CallResolution<T> resolveCall(
      List<CallableTarget<T>> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span,
      Syntax.Expression extensionReceiver,
      boolean nullableAccess) {
    if (candidates.isEmpty()) return null;
    List<SemanticType> explicitTypes =
        explicitTypeArguments.stream()
            .map(
                argument ->
                    typeSystem.resolveCheckedType(argument, resolution.activeTypeParameters))
            .toList();
    List<CallableTarget<T>> arityMatches =
        candidates.stream()
            .filter(
                candidate ->
                    explicitTypes.isEmpty()
                        || explicitTypes.size()
                                >= TypeArguments.required(
                                    candidate.typeParameters(),
                                    parameter -> parameter.defaultType().isPresent())
                            && explicitTypes.size() <= candidate.typeParameters().size())
            .toList();
    if (arityMatches.isEmpty()) {
      if (candidates.size() == 1)
        typeSystem.validateSemanticTypeArgumentCount(
            candidates.getFirst().name(),
            candidates.getFirst().typeParameters(),
            explicitTypeArguments,
            span);
      else
        diagnostics.error(
            INVALID_CALL,
            "no overload of '"
                + candidates.getFirst().name()
                + "' accepts "
                + explicitTypes.size()
                + " type argument(s)",
            span);
      analyzeArguments(call.arguments());
      return null;
    }
    List<CallCandidate<T>> structural = new ArrayList<>();
    for (CallableTarget<T> candidate : arityMatches) {
      List<Integer> indices =
          sourceArgumentIndices(call, candidate.parameters(), extensionReceiver, false);
      if (indices == null) continue;
      structural.add(
          callCandidate(
              candidate.declaration(),
              candidate.typeParameters(),
              candidate.parameters(),
              candidate.result(),
              explicitTypes,
              !explicitTypes.isEmpty(),
              sourceCall(call, extensionReceiver),
              indices,
              expected,
              candidate.ownerSubstitutions(),
              nullableAccess));
    }
    if (structural.isEmpty()) {
      if (arityMatches.size() == 1)
        sourceArgumentIndices(call, arityMatches.getFirst().parameters(), extensionReceiver, true);
      else
        diagnostics.error(
            INVALID_CALL,
            "no overload accepts the supplied argument labels and count",
            call.span());
      return null;
    }
    return selectCandidate(structural, call, expected, span, extensionReceiver, nullableAccess);
  }

  private <T> CallResolution<T> selectCandidate(
      List<CallCandidate<T>> structural,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span,
      Syntax.Expression extensionReceiver,
      boolean nullableAccess) {
    List<CallCandidate<T>> applicable =
        structural.stream().filter(CallCandidate::applicable).toList();
    int bestScore =
        applicable.stream().mapToInt(CallCandidate::score).min().orElse(Integer.MAX_VALUE);
    List<CallCandidate<T>> best =
        applicable.stream().filter(candidate -> candidate.score() == bestScore).toList();
    if (best.size() == 1) return best.getFirst().resolution();
    if (best.size() > 1) {
      diagnostics.error(INVALID_CALL, "call is ambiguous between multiple overloads", span);
      return null;
    }
    if (structural.size() == 1) {
      CallCandidate<T> candidate = structural.getFirst();
      reportInferenceFailures(candidate, span);
      if (extensionReceiver == null) {
        validateArguments(call, candidate.parameters());
      } else {
        expressions.typeOf(extensionReceiver, candidate.parameters().getFirst().type());
        validateArguments(call, candidate.parameters().subList(1, candidate.parameters().size()));
      }
      if (expected != null
          && !expected.equals(SemanticType.DYNAMIC)
          && !typeSystem.isAssignable(
              expected, contextualResult(candidate.resolution().result(), nullableAccess))) {
        diagnostics.error(
            TYPE_MISMATCH,
            "expected "
                + expected.displayName()
                + " but found "
                + contextualResult(candidate.resolution().result(), nullableAccess).displayName(),
            span);
      }
    } else {
      diagnostics.error(INVALID_CALL, "no overload accepts the supplied argument types", span);
    }
    return null;
  }

  private List<Integer> sourceArgumentIndices(
      Syntax.Call call,
      List<ParameterInfo> parameters,
      Syntax.Expression extensionReceiver,
      boolean report) {
    if (extensionReceiver == null) {
      return arguments.argumentIndices(call, parameters, report);
    }
    if (parameters.isEmpty()) return null;
    List<Integer> visible =
        arguments.argumentIndices(call, parameters.subList(1, parameters.size()), report);
    if (visible == null) return null;
    List<Integer> result = new ArrayList<>(visible.size() + 1);
    result.add(0);
    visible.stream().map(index -> index + 1).forEach(result::add);
    return List.copyOf(result);
  }

  private static Syntax.Call sourceCall(Syntax.Call call, Syntax.Expression extensionReceiver) {
    if (extensionReceiver == null) return call;
    List<Syntax.CallArgument> arguments = new ArrayList<>(call.arguments().size() + 1);
    arguments.add(
        new Syntax.CallArgument(Optional.empty(), extensionReceiver, extensionReceiver.span()));
    arguments.addAll(call.arguments());
    return new Syntax.Call(call.callee(), arguments, call.span());
  }

  private <T> CallCandidate<T> callCandidate(
      T declaration,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> patterns,
      SemanticType resultPattern,
      List<SemanticType> explicitTypes,
      boolean hasExplicitTypes,
      Syntax.Call call,
      List<Integer> argumentIndices,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      boolean nullableAccess) {
    Set<String> callableParameterIds = solverVariables(typeParameters);
    Map<String, SemanticType> substitutions = new LinkedHashMap<>(ownerSubstitutions);
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            typeParameters.stream().map(TypeParameterInfo::type).toList(),
            typeSystem.typeRelations);
    if (hasExplicitTypes) {
      for (int index = 0; index < explicitTypes.size(); index++)
        substitutions.put(typeParameters.get(index).type().identity(), explicitTypes.get(index));
    } else {
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        SemanticType pattern = resultPattern.substitute(ownerSubstitutions);
        SemanticType inferenceExpected =
            nullableAccess && !pattern.isNullable() ? expected.nonNullable() : expected;
        solver.constrain(pattern, inferenceExpected);
        substitutions.putAll(solver.solve().substitutions());
      }
      for (int index = 0; index < call.arguments().size(); index++) {
        SemanticType pattern =
            patterns.get(argumentIndices.get(index)).type().substitute(substitutions);
        Syntax.Expression argument = call.arguments().get(index).value();
        SemanticType probeExpected =
            containsTypeParameter(pattern, callableParameterIds)
                    && !(argument instanceof Syntax.Lambda && pattern.isFunction())
                ? null
                : pattern;
        TypeProbe probe = expressions.probeType(argument, probeExpected);
        solver.constrain(pattern, probe.type());
      }
    }
    List<String> missing = new ArrayList<>();
    TypeConstraintSolver.Solution inferred = solver.solve();
    if (!hasExplicitTypes) substitutions.putAll(inferred.substitutions());
    substitutions.putAll(
        TypeArguments.completeInferred(
            typeParameters,
            substitutions,
            parameter -> {
              missing.add(parameter.name());
              return SemanticType.DYNAMIC;
            }));
    Map<String, String> parameterNames =
        typeParameters.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(), TypeParameterInfo::name));
    List<InferenceConflict> conflicts =
        inferred.conflicts().stream()
            .map(
                conflict ->
                    new InferenceConflict(
                        parameterNames.get(conflict.variable()),
                        conflict.first(),
                        conflict.second()))
            .toList();
    List<ParameterInfo> parameters =
        patterns.stream()
            .map(
                parameter ->
                    new ParameterInfo(
                        parameter.name(),
                        parameter.type().substitute(substitutions),
                        parameter.hasDefault()))
            .toList();
    SemanticType result = resultPattern.substitute(substitutions);
    boolean assignable = true;
    List<BoundViolation> boundViolations = new ArrayList<>();
    for (TypeParameterInfo parameter : typeParameters) {
      if (parameter.upperBound().isEmpty()) continue;
      SemanticType actual = substitutions.get(parameter.type().identity());
      SemanticType bound = parameter.upperBound().orElseThrow().substitute(substitutions);
      if (actual != null && !typeSystem.isAssignable(bound, actual)) {
        assignable = false;
        boundViolations.add(new BoundViolation(parameter.name(), bound, actual));
      }
    }
    int score = parameters.size() - call.arguments().size();
    if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
      SemanticType contextualResult = contextualResult(result, nullableAccess);
      if (!typeSystem.isAssignable(expected, contextualResult)) assignable = false;
      else if (!expected.equals(contextualResult)) score++;
    }
    for (int index = 0; index < call.arguments().size(); index++) {
      int parameterIndex = argumentIndices.get(index);
      SemanticType parameter = parameters.get(parameterIndex).type();
      Syntax.Expression argument = call.arguments().get(index).value();
      TypeProbe probe = expressions.probeType(argument, parameter);
      SemanticType actual =
          argument instanceof Syntax.NullLiteral ? SemanticType.NULL : probe.type();
      if (probe.hasErrors() || !typeSystem.isAssignable(parameter, actual)) assignable = false;
      score +=
          callCompatibilityScore(
              patterns.get(parameterIndex).type(), parameter, actual, callableParameterIds);
      TypeProbe intrinsicProbe = expressions.probeType(argument, null);
      if (!intrinsicProbe.hasErrors() && !parameter.equals(intrinsicProbe.type())) {
        score += typeSystem.isAssignable(parameter, intrinsicProbe.type()) ? 1 : 2;
      }
    }
    List<SemanticType> reifiedArguments =
        typeParameters.stream()
            .map(parameter -> substitutions.get(parameter.type().identity()))
            .toList();
    CallResolution<T> resolved =
        new CallResolution<>(declaration, parameters, reifiedArguments, result);
    return new CallCandidate<>(
        resolved,
        List.copyOf(missing),
        List.copyOf(conflicts),
        List.copyOf(boundViolations),
        assignable,
        score);
  }

  private static SemanticType contextualResult(SemanticType result, boolean nullableAccess) {
    if (!nullableAccess
        || result.kind() == SemanticType.Kind.VOID
        || result.equals(SemanticType.DYNAMIC)) {
      return result;
    }
    return result.nullable();
  }

  static int callCompatibilityScore(
      SemanticType pattern,
      SemanticType parameter,
      SemanticType actual,
      Set<String> callableParameterIds) {
    int score;
    if (actual.equals(SemanticType.NULL)) {
      score = containsTypeParameter(pattern, callableParameterIds) ? 4 : 2;
    } else if (containsDynamic(actual)) {
      score = 4;
    } else {
      score = parameter.equals(actual) ? 0 : 1;
      if (containsTypeParameter(pattern, callableParameterIds)) score += 3;
    }
    return score;
  }

  static boolean containsDynamic(SemanticType type) {
    if (type.equals(SemanticType.DYNAMIC)) return true;
    return type.arguments().stream().anyMatch(CallResolver::containsDynamic);
  }

  static boolean containsTypeParameter(SemanticType type, Set<String> identities) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && identities.contains(type.identity())) {
      return true;
    }
    return type.arguments().stream()
        .anyMatch(argument -> containsTypeParameter(argument, identities));
  }

  void reportInferenceFailures(CallCandidate<?> candidate, SourceSpan span) {
    for (String name : candidate.missingTypeArguments()) {
      diagnostics.error(INVALID_CALL, "cannot infer type argument '" + name + "'", span);
    }
    for (InferenceConflict conflict : candidate.conflicts()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + conflict.name()
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          span);
    }
    for (BoundViolation violation : candidate.boundViolations()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type argument '"
              + violation.actual().displayName()
              + "' does not satisfy bound '"
              + violation.bound().displayName()
              + "' for '"
              + violation.name()
              + "'",
          span);
    }
  }

  CallResolution<SymbolId> resolveConstructor(
      Syntax.AggregateDecl declaration,
      SemanticType constructedType,
      Syntax.Call call,
      SourceSpan span) {
    return resolveCall(
        constructorCandidates(declaration, constructedType, List.of()),
        List.of(),
        call,
        null,
        span,
        null,
        false);
  }

  private List<CallableTarget<SymbolId>> constructorCandidates(
      Syntax.AggregateDecl declaration,
      SemanticType constructedType,
      List<TypeParameterInfo> typeParameters) {
    Map<String, SemanticType> substitutions =
        typeSystem.aggregateSubstitutions(declaration, constructedType);
    List<CallableTarget<SymbolId>> candidates;
    if (declaration.constructors().isEmpty()) {
      candidates =
          List.of(
              new CallableTarget<>(
                  model.declarationSymbols().get(declaration),
                  declaration.name(),
                  typeParameters,
                  typeSystem.fieldParameters(declaration, substitutions),
                  constructedType,
                  Map.of()));
    } else {
      Map<String, SemanticType> declared = typeSystem.aggregateTypeParameters(declaration);
      candidates =
          declaration.constructors().stream()
              .map(
                  constructor ->
                      new CallableTarget<>(
                          model.declarationSymbols().get(constructor),
                          declaration.name(),
                          typeParameters,
                          typeSystem.parameters(constructor.parameters(), substitutions, declared),
                          constructedType,
                          Map.<String, SemanticType>of()))
              .toList();
    }
    return candidates;
  }

  ArgumentBinding validateArguments(Syntax.Call call, List<ParameterInfo> parameters) {
    List<Integer> parameterIndices = arguments.argumentIndices(call, parameters, true);
    for (int index = 0; index < call.arguments().size(); index++) {
      Syntax.CallArgument argument = call.arguments().get(index);
      int parameterIndex = parameterIndices.get(index);
      if (parameterIndex >= 0) {
        ParameterInfo parameter = parameters.get(parameterIndex);
        typeSystem.requireAssignable(
            parameter.type(),
            expressions.typeOf(argument.value(), parameter.type()),
            argument.span());
      } else {
        expressions.typeOf(argument.value(), null);
      }
    }
    return new ArgumentBinding(parameterIndices);
  }

  SemanticType recordCall(
      Syntax.Call call,
      SourceSpan calleeSpan,
      ResolvedCall.Kind kind,
      SymbolId target,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    ArgumentBinding arguments = validateArguments(call, parameters);
    if (arguments.parameterIndices().stream()
        .anyMatch(index -> index < 0 || index >= parameters.size())) {
      return result;
    }
    model.putCall(
        call.span(),
        new ResolvedCall(
            kind, target, calleeSpan, arguments, parameters, reifiedArguments, result));
    return result;
  }

  SemanticType recordExtensionCall(
      Syntax.Member member,
      Syntax.Call call,
      SymbolId target,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    if (parameters.isEmpty()) return result;
    expressions.typeOf(member.receiver(), parameters.getFirst().type());
    List<ParameterInfo> visible = parameters.subList(1, parameters.size());
    ArgumentBinding binding = validateArguments(call, visible);
    List<Integer> indices = binding.parameterIndices().stream().map(index -> index + 1).toList();
    model.putCall(
        call.span(),
        new ResolvedCall(
            ResolvedCall.Kind.EXTENSION,
            target,
            member.nameSpan(),
            new ArgumentBinding(indices),
            parameters,
            reifiedArguments,
            result));
    return result;
  }

  void analyzeArguments(List<Syntax.CallArgument> arguments) {
    for (Syntax.CallArgument argument : arguments) {
      expressions.typeOf(argument.value(), null);
    }
  }

  CallResolution<SymbolId> resolveConstruction(
      Syntax.Name name, Syntax.Call call, SemanticType expected) {
    Symbol builtin = builtins.type(name.value()).orElse(null);
    Syntax.AggregateDecl source = typeSystem.resolveAggregate(name.value());
    List<TypeParameterInfo> typeParameters = List.of();
    SemanticType constructedType;
    if (name.diamond()) {
      typeParameters =
          builtin != null
              ? builtin.typeParameters()
              : typeSystem.symbolTypeParameters(
                  source.typeParameters(), typeSystem.aggregateTypeParameters(source));
      List<SemanticType> variables = typeParameters.stream().map(TypeParameterInfo::type).toList();
      constructedType =
          builtin != null
              ? builtins.instantiate(name.value(), variables)
              : typeSystem.sourceType(name.value(), variables);
      if (typeParameters.isEmpty()) {
        diagnostics.error(INVALID_CALL, "diamond requires a generic type constructor", name.span());
        return null;
      }
    } else {
      constructedType = typeSystem.appliedType(name.value(), name.typeArguments(), name.span());
    }
    List<CallableTarget<SymbolId>> candidates;
    if (builtin != null) {
      var parameters = builtins.constructorParameters(constructedType);
      if (parameters.isEmpty()) {
        diagnostics.error(
            INVALID_CALL, "type '" + name.value() + "' has no constructor", name.span());
        return null;
      }
      candidates =
          List.of(
              new CallableTarget<>(
                  builtin.id(),
                  name.value(),
                  typeParameters,
                  parameters.orElseThrow(),
                  constructedType,
                  Map.of()));
    } else {
      candidates = constructorCandidates(source, constructedType, typeParameters);
    }
    return resolveCall(candidates, List.of(), call, expected, name.span(), null, false);
  }

  static Set<String> solverVariables(List<TypeParameterInfo> parameters) {
    return parameters.stream()
        .map(parameter -> parameter.type().identity())
        .collect(java.util.stream.Collectors.toSet());
  }

  private record CallableTarget<T>(
      T declaration,
      String name,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters,
      SemanticType result,
      Map<String, SemanticType> ownerSubstitutions) {
    private CallableTarget {
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
      ownerSubstitutions = Map.copyOf(ownerSubstitutions);
    }
  }

  record CallResolution<T>(
      T declaration,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    CallResolution {
      parameters = List.copyOf(parameters);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  record CallCandidate<T>(
      CallResolution<T> resolution,
      List<String> missingTypeArguments,
      List<InferenceConflict> conflicts,
      List<BoundViolation> boundViolations,
      boolean assignable,
      int score) {
    CallCandidate {
      missingTypeArguments = List.copyOf(missingTypeArguments);
      conflicts = List.copyOf(conflicts);
      boundViolations = List.copyOf(boundViolations);
    }

    boolean applicable() {
      return missingTypeArguments.isEmpty()
          && conflicts.isEmpty()
          && boundViolations.isEmpty()
          && assignable;
    }

    List<ParameterInfo> parameters() {
      return resolution.parameters();
    }
  }

  record InferenceConflict(String name, SemanticType first, SemanticType second) {}

  record BoundViolation(String name, SemanticType bound, SemanticType actual) {}
}
