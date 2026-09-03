package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.BoundViolation;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.ControlKind;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.InferenceConflict;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.SourceCallCandidate;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.SourceCallResolution;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.TypeProbe;
import dev.w0fv1.norm.semantic.ArgumentBinding;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.TypeRelations;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.LexicalLifetime;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TypeSystem {
  private final Analyzer analyzer;

  TypeSystem(Analyzer analyzer) {
    this.analyzer = java.util.Objects.requireNonNull(analyzer, "analyzer");
  }

  SourceCallResolution resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      String callableKind) {
    return resolveSourceCall(
        candidates,
        explicitTypeArguments,
        call,
        expected,
        ownerSubstitutions,
        span,
        callableKind,
        false);
  }

  SourceCallResolution resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      String callableKind,
      boolean nullableAccess) {
    return resolveSourceCall(
        candidates,
        explicitTypeArguments,
        call,
        expected,
        ownerSubstitutions,
        span,
        callableKind,
        null,
        nullableAccess);
  }

  SourceCallResolution resolveExtensionCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Expression receiver,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span) {
    return resolveSourceCall(
        candidates,
        explicitTypeArguments,
        call,
        expected,
        Map.of(),
        span,
        "extension",
        receiver,
        false);
  }

  private SourceCallResolution resolveSourceCall(
      List<Syntax.FunctionDecl> candidates,
      List<Syntax.TypeRef> explicitTypeArguments,
      Syntax.Call call,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      SourceSpan span,
      String callableKind,
      Syntax.Expression extensionReceiver,
      boolean nullableAccess) {
    if (candidates.isEmpty()) return null;
    List<SemanticType> explicitTypes =
        explicitTypeArguments.stream()
            .map(argument -> resolveCheckedType(argument, analyzer.context.activeTypeParameters))
            .toList();
    List<Syntax.FunctionDecl> arityMatches =
        explicitTypeArguments.isEmpty()
            ? candidates
            : candidates.stream()
                .filter(
                    declaration ->
                        declaration.typeParameters().size() == explicitTypeArguments.size())
                .toList();
    if (arityMatches.isEmpty()) {
      if (candidates.size() == 1) {
        validateTypeArgumentCount(
            candidates.getFirst().name(),
            candidates.getFirst().typeParameters().size(),
            explicitTypeArguments,
            span);
      } else {
        analyzer.context.diagnostics.error(
            INVALID_CALL,
            "no overload of "
                + callableKind
                + " '"
                + candidates.getFirst().name()
                + "' accepts "
                + explicitTypeArguments.size()
                + " type argument(s)",
            span);
      }
      analyzeArguments(call.arguments());
      return null;
    }

    List<SourceCallCandidate> structural = new ArrayList<>();
    for (Syntax.FunctionDecl declaration : arityMatches) {
      List<ParameterInfo> unresolved = parametersOf(declaration, ownerSubstitutions);
      List<Integer> indices = sourceArgumentIndices(call, unresolved, extensionReceiver, false);
      if (indices != null) {
        structural.add(
            sourceCallCandidate(
                declaration,
                explicitTypes,
                !explicitTypeArguments.isEmpty(),
                sourceCall(call, extensionReceiver),
                indices,
                expected,
                ownerSubstitutions,
                nullableAccess));
      }
    }
    if (structural.isEmpty()) {
      if (arityMatches.size() == 1) {
        sourceArgumentIndices(
            call,
            parametersOf(arityMatches.getFirst(), ownerSubstitutions),
            extensionReceiver,
            true);
      } else {
        analyzer.context.diagnostics.error(
            INVALID_CALL,
            "no overload accepts the supplied argument labels and count",
            call.span());
      }
      return null;
    }

    List<SourceCallCandidate> applicable =
        structural.stream().filter(SourceCallCandidate::applicable).toList();
    int bestScore =
        applicable.stream().mapToInt(SourceCallCandidate::score).min().orElse(Integer.MAX_VALUE);
    List<SourceCallCandidate> best =
        applicable.stream().filter(candidate -> candidate.score() == bestScore).toList();
    if (best.size() == 1) return best.getFirst().resolution();
    if (best.size() > 1) {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "call is ambiguous between multiple overloads", span);
      return null;
    }
    if (structural.size() == 1) {
      SourceCallCandidate candidate = structural.getFirst();
      reportInferenceFailures(candidate, span);
      if (extensionReceiver == null) {
        validateArguments(call, candidate.parameters());
      } else {
        analyzer.typeOf(extensionReceiver, candidate.parameters().getFirst().type());
        validateArguments(call, candidate.parameters().subList(1, candidate.parameters().size()));
      }
      if (expected != null
          && !expected.equals(SemanticType.DYNAMIC)
          && !isPotentiallyAssignable(
              expected, contextualResult(candidate.resolution().result(), nullableAccess))) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "expected "
                + expected.displayName()
                + " but found "
                + contextualResult(candidate.resolution().result(), nullableAccess).displayName(),
            span);
      }
    } else {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "no overload accepts the supplied argument types", span);
    }
    return null;
  }

  private List<Integer> sourceArgumentIndices(
      Syntax.Call call,
      List<ParameterInfo> parameters,
      Syntax.Expression extensionReceiver,
      boolean report) {
    if (extensionReceiver == null) {
      return analyzer.context.overloads.argumentIndices(call, parameters, report);
    }
    if (parameters.isEmpty()) return null;
    List<Integer> visible =
        analyzer.context.overloads.argumentIndices(
            call, parameters.subList(1, parameters.size()), report);
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

  SourceCallCandidate sourceCallCandidate(
      Syntax.FunctionDecl declaration,
      List<SemanticType> explicitTypes,
      boolean hasExplicitTypes,
      Syntax.Call call,
      List<Integer> argumentIndices,
      SemanticType expected,
      Map<String, SemanticType> ownerSubstitutions,
      boolean nullableAccess) {
    Map<String, SemanticType> callableParameters = functionTypeParameters(declaration);
    Set<String> callableParameterIds =
        callableParameters.values().stream()
            .map(SemanticType::identity)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, SemanticType> substitutions = new LinkedHashMap<>(ownerSubstitutions);
    TypeConstraintSolver solver = new TypeConstraintSolver(callableParameters.values());
    Map<String, SemanticType> declarationTypes = typeParameters(declaration, ownerOf(declaration));
    if (hasExplicitTypes) {
      for (int index = 0; index < declaration.typeParameters().size(); index++) {
        SemanticType parameter =
            callableParameters.get(declaration.typeParameters().get(index).name());
        substitutions.put(parameter.identity(), explicitTypes.get(index));
      }
    } else {
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        SemanticType pattern =
            analyzer
                .functionReturnType(declaration, declarationTypes)
                .substitute(ownerSubstitutions);
        SemanticType inferenceExpected =
            nullableAccess && !pattern.isNullable() ? expected.nonNullable() : expected;
        constrainInference(solver, pattern, inferenceExpected);
        substitutions.putAll(solver.solve().substitutions());
      }
      for (int index = 0; index < call.arguments().size(); index++) {
        Syntax.Parameter parameter = declaration.parameters().get(argumentIndices.get(index));
        SemanticType pattern =
            resolveDeclarationType(parameter.type(), declaration, declarationTypes)
                .substitute(substitutions);
        SemanticType probeExpected =
            containsTypeParameter(pattern, callableParameterIds) ? null : pattern;
        TypeProbe probe = analyzer.probeType(call.arguments().get(index).value(), probeExpected);
        constrainInference(solver, pattern, probe.type());
      }
    }

    List<String> missing = new ArrayList<>();
    List<InferenceConflict> conflicts = new ArrayList<>();
    TypeConstraintSolver.Solution inferredTypes = solver.solve();
    for (Syntax.TypeParameter parameterSyntax : declaration.typeParameters()) {
      SemanticType parameter = callableParameters.get(parameterSyntax.name());
      SemanticType inferred = inferredTypes.substitutions().get(parameter.identity());
      if (!hasExplicitTypes && inferred == null) {
        missing.add(parameterSyntax.name());
        substitutions.put(parameter.identity(), SemanticType.DYNAMIC);
      } else if (!hasExplicitTypes) {
        substitutions.put(parameter.identity(), inferred);
      }
    }
    Map<String, String> parameterNames =
        callableParameters.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    entry -> entry.getValue().identity(),
                    Map.Entry::getKey,
                    (left, right) -> left));
    inferredTypes
        .conflicts()
        .forEach(
            conflict ->
                conflicts.add(
                    new InferenceConflict(
                        parameterNames.get(conflict.variable()),
                        conflict.first(),
                        conflict.second())));
    List<ParameterInfo> parameters = parametersOf(declaration, substitutions);
    SemanticType result =
        analyzer.functionReturnType(declaration, declarationTypes).substitute(substitutions);
    boolean assignable = true;
    List<BoundViolation> boundViolations = new ArrayList<>();
    for (Syntax.TypeParameter parameterSyntax : declaration.typeParameters()) {
      if (parameterSyntax.upperBound().isEmpty()) continue;
      SemanticType parameter = callableParameters.get(parameterSyntax.name());
      SemanticType actual = substitutions.get(parameter.identity());
      SemanticType bound =
          resolveDeclarationType(
                  parameterSyntax.upperBound().orElseThrow(), declaration, declarationTypes)
              .substitute(substitutions);
      if (actual != null && !isAssignable(bound, actual)) {
        assignable = false;
        boundViolations.add(new BoundViolation(parameterSyntax.name(), bound, actual));
      }
    }
    int score = 0;
    if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
      SemanticType contextualResult = contextualResult(result, nullableAccess);
      if (!isPotentiallyAssignable(expected, contextualResult)) assignable = false;
      else if (!expected.equals(contextualResult)) score++;
    }
    List<ParameterInfo> patterns = parametersOf(declaration, ownerSubstitutions);
    for (int index = 0; index < call.arguments().size(); index++) {
      int parameterIndex = argumentIndices.get(index);
      SemanticType parameter = parameters.get(parameterIndex).type();
      Syntax.Expression argument = call.arguments().get(index).value();
      TypeProbe probe = analyzer.probeType(argument, parameter);
      SemanticType actual =
          argument instanceof Syntax.NullLiteral ? SemanticType.NULL : probe.type();
      if (probe.hasErrors() || !isPotentiallyAssignable(parameter, actual)) assignable = false;
      score +=
          callCompatibilityScore(
              patterns.get(parameterIndex).type(), parameter, actual, callableParameterIds);
      TypeProbe intrinsicProbe = analyzer.probeType(argument, null);
      if (!intrinsicProbe.hasErrors() && !parameter.equals(intrinsicProbe.type())) {
        score += isPotentiallyAssignable(parameter, intrinsicProbe.type()) ? 1 : 2;
      }
    }
    List<SemanticType> reifiedArguments =
        declaration.typeParameters().stream()
            .map(
                parameter -> substitutions.get(callableParameters.get(parameter.name()).identity()))
            .toList();
    SourceCallResolution resolution =
        new SourceCallResolution(declaration, parameters, reifiedArguments, result);
    return new SourceCallCandidate(
        resolution,
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

  void constrainInference(TypeConstraintSolver solver, SemanticType pattern, SemanticType actual) {
    if (actual.isReference()) return;
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      solver.constrain(pattern, actual);
      return;
    }
    SemanticType alignedActual = inferenceView(pattern, actual);
    if (!pattern.nonNullable().identity().equals(alignedActual.nonNullable().identity())) {
      SemanticType alignedPattern = inferenceView(actual, pattern);
      if (alignedPattern.nonNullable().identity().equals(actual.nonNullable().identity())) {
        solver.constrain(alignedPattern, actual);
      }
      return;
    }
    solver.constrain(pattern, alignedActual);
  }

  SemanticType inferenceView(SemanticType pattern, SemanticType actual) {
    if (pattern.nonNullable().identity().equals(actual.nonNullable().identity())) return actual;
    for (SemanticType view : nominalViews(actual.nonNullable())) {
      if (pattern.nonNullable().identity().equals(view.nonNullable().identity())) {
        return actual.isNullable() ? view.nullable() : view;
      }
    }
    return actual;
  }

  List<SemanticType> nominalViews(SemanticType actual) {
    List<SemanticType> result =
        new ArrayList<>(analyzer.context.builtins.protocolConformances(actual));
    Syntax.AggregateDecl concrete = resolveAggregate(actual);
    if (concrete == null) return List.copyOf(result);
    Syntax.Program previous = analyzer.context.currentProgram;
    analyzer.context.currentProgram = analyzer.context.declarations.owner(concrete);
    Map<String, SemanticType> substitutions = aggregateSubstitutions(concrete, actual);
    Map<String, SemanticType> aggregateParameters = aggregateTypeParameters(concrete);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (Syntax.TypeRef interfaceRef : concrete.implementedInterfaces()) {
      SemanticType conformance =
          resolveType(interfaceRef, aggregateParameters).substitute(substitutions);
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration != null) {
        analyzer.collectConformances(declaration, conformance, conformances, interfaceRef.span());
      }
    }
    analyzer.context.currentProgram = previous;
    result.addAll(conformances.values());
    return List.copyOf(result);
  }

  boolean isPotentiallyAssignable(SemanticType expected, SemanticType actual) {
    if (expected.equals(SemanticType.DYNAMIC) || actual.equals(SemanticType.DYNAMIC)) return true;
    if (actual.equals(SemanticType.NULL)) return expected.mayContainNull();
    if (expected.equals(SemanticType.NULL)) return false;
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (!expected.nonNullable().identity().equals(actual.nonNullable().identity())
        || expected.arguments().size() != actual.arguments().size()) {
      return isAssignable(expected, actual);
    }
    for (int index = 0; index < expected.arguments().size(); index++) {
      if (expected.arguments().get(index).kind() == SemanticType.Kind.EXISTENTIAL) continue;
      if (!isPotentiallyAssignable(
          expected.arguments().get(index), actual.arguments().get(index))) {
        return false;
      }
    }
    return true;
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
    return type.arguments().stream().anyMatch(TypeSystem::containsDynamic);
  }

  static boolean containsTypeParameter(SemanticType type, Set<String> identities) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && identities.contains(type.identity())) {
      return true;
    }
    return type.arguments().stream()
        .anyMatch(argument -> containsTypeParameter(argument, identities));
  }

  void reportInferenceFailures(SourceCallCandidate candidate, SourceSpan span) {
    for (String name : candidate.missingTypeArguments()) {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "cannot infer type argument '" + name + "'", span);
    }
    for (InferenceConflict conflict : candidate.conflicts()) {
      analyzer.context.diagnostics.error(
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
      analyzer.context.diagnostics.error(
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

  Symbol selectBuiltinOverload(List<Symbol> candidates, Syntax.Call call, SourceSpan span) {
    OverloadResolver.Candidate selected =
        analyzer.context.overloads.select(
            candidates.stream()
                .map(candidate -> new OverloadResolver.Candidate(candidate, candidate.parameters()))
                .toList(),
            call,
            span);
    return selected == null ? null : (Symbol) selected.target();
  }

  OverloadResolver.Candidate resolveConstructor(
      Syntax.AggregateDecl declaration,
      SemanticType constructedType,
      Syntax.Call call,
      SourceSpan span) {
    Map<String, SemanticType> substitutions = aggregateSubstitutions(declaration, constructedType);
    List<OverloadResolver.Candidate> candidates;
    if (declaration.constructors().isEmpty()) {
      candidates =
          List.of(
              new OverloadResolver.Candidate(
                  declaration, fieldParameters(declaration, substitutions)));
    } else {
      Map<String, SemanticType> declared = aggregateTypeParameters(declaration);
      candidates =
          declaration.constructors().stream()
              .map(
                  constructor ->
                      new OverloadResolver.Candidate(
                          constructor,
                          parameters(constructor.parameters(), substitutions, declared)))
              .toList();
    }
    return analyzer.context.overloads.select(candidates, call, span);
  }

  ArgumentBinding validateArguments(Syntax.Call call, List<ParameterInfo> parameters) {
    List<Integer> parameterIndices =
        analyzer.context.overloads.argumentIndices(call, parameters, true);
    for (int index = 0; index < call.arguments().size(); index++) {
      Syntax.CallArgument argument = call.arguments().get(index);
      int parameterIndex = parameterIndices.get(index);
      if (parameterIndex >= 0) {
        ParameterInfo parameter = parameters.get(parameterIndex);
        requireAssignable(
            parameter.type(), analyzer.typeOf(argument.value(), parameter.type()), argument.span());
      } else {
        analyzer.typeOf(argument.value(), null);
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
    analyzer.context.resolvedCalls.put(
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
    analyzer.typeOf(member.receiver(), parameters.getFirst().type());
    List<ParameterInfo> visible = parameters.subList(1, parameters.size());
    ArgumentBinding binding = validateArguments(call, visible);
    List<Integer> indices = binding.parameterIndices().stream().map(index -> index + 1).toList();
    analyzer.context.resolvedCalls.put(
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
      analyzer.typeOf(argument.value(), null);
    }
  }

  void validateType(Syntax.TypeRef type, boolean allowVoid) {
    validateType(type, allowVoid, false);
  }

  void validateReferenceCapableType(Syntax.TypeRef type) {
    validateType(type, false, true);
  }

  private void validateType(
      Syntax.TypeRef type, boolean allowVoid, boolean allowTopLevelReference) {
    if (type.isWildcard()) return;
    if (type.name().equals("ref")) {
      if (!allowTopLevelReference) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "ref is only valid as a local or callable parameter type", type.span());
      }
      if (type.arguments().size() != 1) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "type 'ref' requires 1 type argument, found " + type.arguments().size(),
            type.span());
        type.arguments().forEach(argument -> validateType(argument, false, false));
        return;
      }
      if (type.nullable()) {
        analyzer.context.diagnostics.error(
            INVALID_NULLABLE_TYPE, "ref cannot be nullable", type.span());
      }
      Syntax.TypeRef targetSyntax = type.arguments().getFirst();
      validateType(targetSyntax, false, false);
      SemanticType target = resolveType(targetSyntax, analyzer.context.activeTypeParameters);
      if (target.isReference() || target.isNullable() || target.category() != ValueCategory.VALUE) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH, "ref target must be a non-reference value type", targetSyntax.span());
      }
      return;
    }
    String name = type.displayName();
    SymbolId typeParameter = analyzer.context.activeTypeParameterSymbols.get(type.name());
    if (typeParameter != null) {
      analyzer.context.bindings.put(type.span(), typeParameter);
    } else {
      SymbolId alias = importedAlias(type.name());
      if (alias != null) {
        analyzer.context.bindings.put(type.span(), alias);
      } else {
        typeSymbol(type.name())
            .ifPresent(symbol -> analyzer.context.bindings.put(type.span(), symbol.id()));
      }
    }
    int arity =
        type.name().equals("Function") ? type.arguments().size() : declaredTypeArity(type.name());
    if (analyzer.context.activeTypeParameters.containsKey(type.name())) arity = 0;
    if (type.name().equals("Function") && type.arguments().isEmpty()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "Function requires a complete call signature", type.span());
      return;
    }
    if (arity < 0 && !analyzer.context.activeTypeParameters.containsKey(type.name())) {
      analyzer.context.diagnostics.error(UNKNOWN_NAME, "unknown type '" + name + "'", type.span());
      return;
    }
    if (!allowVoid && type.name().equals("Void")) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH, "type 'Void' is not valid here", type.span());
      return;
    }
    if (type.nullable() && type.name().equals("Void")) {
      analyzer.context.diagnostics.error(
          INVALID_NULLABLE_TYPE, "Void cannot be nullable", type.span());
      return;
    }
    if (!type.name().equals("Function") && arity != type.arguments().size()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "type '"
              + type.name()
              + "' requires "
              + arity
              + " type argument(s), found "
              + type.arguments().size(),
          type.span());
    }
    for (int index = 0; index < type.arguments().size(); index++) {
      Syntax.TypeRef argument = type.arguments().get(index);
      validateType(argument, type.name().equals("Function") && index == 0, false);
    }
    validateDeclaredTypeBounds(type);
  }

  void validateDeclaredTypeBounds(Syntax.TypeRef reference) {
    if (reference.isWildcard()) return;
    List<Syntax.TypeParameter> parameters;
    Map<String, SemanticType> declared;
    Object declaration;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(reference.name());
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(reference.name());
    Syntax.EnumDecl enumDecl = resolveEnum(reference.name());
    if (interfaceDecl != null) {
      parameters = interfaceDecl.typeParameters();
      declared = interfaceTypeParameters(interfaceDecl);
      declaration = interfaceDecl;
    } else if (aggregateDecl != null) {
      parameters = aggregateDecl.typeParameters();
      declared = aggregateTypeParameters(aggregateDecl);
      declaration = aggregateDecl;
    } else if (enumDecl != null) {
      parameters = enumDecl.typeParameters();
      declared = enumTypeParameters(enumDecl);
      declaration = enumDecl;
    } else {
      return;
    }
    if (parameters.size() != reference.arguments().size()) return;
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    List<SemanticType> actualArguments =
        reference.arguments().stream()
            .map(argument -> resolveType(argument, analyzer.context.activeTypeParameters))
            .toList();
    for (int index = 0; index < parameters.size(); index++) {
      substitutions.put(
          declared.get(parameters.get(index).name()).identity(), actualArguments.get(index));
    }
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      if (parameter.upperBound().isEmpty()) continue;
      SemanticType bound =
          resolveDeclarationType(parameter.upperBound().orElseThrow(), declaration, declared)
              .substitute(substitutions);
      SemanticType actual = actualArguments.get(index);
      if (actual.kind() != SemanticType.Kind.EXISTENTIAL && !isAssignable(bound, actual)) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + actual.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            reference.arguments().get(index).span());
      }
    }
  }

  void requireBoth(SemanticType expected, SemanticType left, SemanticType right, SourceSpan span) {
    requireType(expected, left, span);
    requireType(expected, right, span);
  }

  void requireType(SemanticType expected, SemanticType actual, SourceSpan span) {
    requireAssignable(expected, actual, span);
  }

  void requireAssignable(SemanticType expected, SemanticType actual, SourceSpan span) {
    if (!isAssignable(expected, actual)) {
      DiagnosticCode code =
          actual.mayContainNull() && !expected.mayContainNull()
              ? NULLABILITY_MISMATCH
              : TYPE_MISMATCH;
      analyzer.context.diagnostics.error(
          code, "expected " + expected.displayName() + " but found " + actual.displayName(), span);
    }
  }

  boolean isAssignable(SemanticType expected, SemanticType actual) {
    return analyzer.context.typeRelations.isAssignable(expected, actual);
  }

  Optional<SemanticType> commonType(SemanticType left, SemanticType right) {
    Optional<SemanticType> direct = TypeRelations.commonType(left, right);
    if (direct.isPresent()) return direct;
    SemanticType leftValue = left.nonNullable();
    SemanticType rightValue = right.nonNullable();
    if (!isAssignable(SemanticAnalysisContext.STRINGABLE, leftValue)
        || !isAssignable(SemanticAnalysisContext.STRINGABLE, rightValue)) {
      return Optional.empty();
    }
    return Optional.of(
        left.isNullable() || right.isNullable()
            ? SemanticAnalysisContext.STRINGABLE.nullable()
            : SemanticAnalysisContext.STRINGABLE);
  }

  boolean isNominallyAssignable(SemanticType expected, SemanticType actual) {
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (expected.isNullable() && !actual.isNullable()) {
      return isAssignable(expected.nonNullable(), actual);
    }
    SemanticType bound =
        actual.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? analyzer.context.typeParameterBounds.get(actual.identity())
            : null;
    if (bound != null && isAssignable(expected, bound)) return true;
    for (AggregateView view : aggregateViews(actual.nonNullable())) {
      if (expected.nonNullable().equals(view.type())) return true;
    }
    Syntax.InterfaceDecl required = resolveInterface(expected.nonNullable());
    for (SemanticType conformance :
        analyzer.context.builtins.protocolConformances(actual.nonNullable())) {
      if (expected.nonNullable().equals(conformance)) return true;
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration != null) {
        Map<String, SemanticType> inherited = new LinkedHashMap<>();
        analyzer.collectConformances(
            declaration, conformance, inherited, analyzer.context.currentProgram.span());
        if (expected.nonNullable().equals(inherited.get(expected.identity()))) return true;
      }
    }
    if (required == null) return false;
    Syntax.InterfaceDecl actualInterface = resolveInterface(actual.nonNullable());
    if (actualInterface != null) {
      Map<String, SemanticType> inherited = new LinkedHashMap<>();
      analyzer.collectConformances(
          actualInterface, actual.nonNullable(), inherited, analyzer.context.currentProgram.span());
      return expected.nonNullable().equals(inherited.get(expected.identity()));
    }
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    Syntax.Program previous = analyzer.context.currentProgram;
    for (AggregateView view : aggregateViews(actual.nonNullable())) {
      analyzer.context.currentProgram = analyzer.context.declarations.owner(view.declaration());
      Map<String, SemanticType> substitutions =
          aggregateSubstitutions(view.declaration(), view.type());
      Map<String, SemanticType> aggregateParameters = aggregateTypeParameters(view.declaration());
      for (Syntax.TypeRef interfaceRef : view.declaration().implementedInterfaces()) {
        SemanticType conformance =
            resolveType(interfaceRef, aggregateParameters).substitute(substitutions);
        Syntax.InterfaceDecl declaration = resolveInterface(conformance);
        if (declaration != null) {
          analyzer.collectConformances(declaration, conformance, conformances, interfaceRef.span());
        }
      }
    }
    analyzer.context.currentProgram = previous;
    return expected.nonNullable().equals(conformances.get(expected.identity()));
  }

  Optional<SemanticType> directParentType(Syntax.AggregateDecl declaration, SemanticType instance) {
    if (declaration.extendedClass().isEmpty()) return Optional.empty();
    Syntax.Program previous = analyzer.context.currentProgram;
    analyzer.context.currentProgram =
        analyzer.context.declarations.ownerOr(declaration, analyzer.context.currentProgram);
    SemanticType parent =
        resolveType(declaration.extendedClass().orElseThrow(), aggregateTypeParameters(declaration))
            .substitute(aggregateSubstitutions(declaration, instance));
    analyzer.context.currentProgram = previous;
    return Optional.of(parent);
  }

  List<AggregateView> aggregateViews(SemanticType instance) {
    List<AggregateView> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    SemanticType current = instance.nonNullable();
    while (visited.add(current.identity())) {
      Syntax.AggregateDecl declaration = resolveAggregate(current);
      if (declaration == null) break;
      result.add(new AggregateView(declaration, current));
      Optional<SemanticType> parent = directParentType(declaration, current);
      if (parent.isEmpty()) break;
      current = parent.orElseThrow().nonNullable();
    }
    return List.copyOf(result);
  }

  void declareExisting(String name, SemanticType type, SourceSpan span, SymbolId id) {
    if (!analyzer.context.flowScopes.declare(name, type, id)) {
      analyzer.context.diagnostics.error(
          DUPLICATE_NAME, "name '" + name + "' is already declared", span);
      return;
    }
    if (type.isReference()) {
      FlowScopes.ScopedSymbol scoped = analyzer.context.flowScopes.find(name);
      Symbol symbol = analyzer.context.symbols.get(id);
      analyzer.context.flowScopes.updateReferenceLifetime(
          scoped,
          symbol != null && symbol.kind() == SymbolKind.PARAMETER
              ? LexicalLifetime.longLived()
              : LexicalLifetime.unusable());
    }
  }

  void declareSelf(SemanticType type, SourceSpan span) {
    SymbolId id = SymbolId.source(span.source().id(), analyzer.context.nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            "this",
            SymbolKind.SELF,
            type,
            Optional.empty(),
            Optional.ofNullable(analyzer.context.currentCallable),
            List.of(),
            List.of(),
            "");
    analyzer.context.symbols.put(id, symbol);
    declareExisting("this", type, span, symbol.id());
  }

  SemanticType lookup(String name, SourceSpan span) {
    FlowScopes.ScopedSymbol symbol = analyzer.context.flowScopes.find(name);
    if (symbol != null) {
      analyzer.context.bindings.put(span, symbol.id());
      return analyzer.context.flowScopes.type(symbol);
    }
    analyzer.context.diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
    return SemanticType.DYNAMIC;
  }

  SemanticType lookupDeclared(String name, SourceSpan span) {
    FlowScopes.ScopedSymbol symbol = findScoped(name);
    if (symbol == null) {
      analyzer.context.diagnostics.error(UNKNOWN_NAME, "cannot find name '" + name + "'", span);
      return SemanticType.DYNAMIC;
    }
    analyzer.context.bindings.put(span, symbol.id());
    return symbol.declaredType();
  }

  FlowScopes.ScopedSymbol findScoped(String name) {
    return analyzer.context.flowScopes.find(name);
  }

  void invalidateNarrowing(String name) {
    FlowScopes.ScopedSymbol symbol = findScoped(name);
    if (symbol == null) return;
    analyzer.context.flowScopes.update(symbol, symbol.declaredType());
  }

  Map<String, SemanticType> narrowingsFor(Syntax.Expression condition, boolean truth) {
    if (condition instanceof Syntax.Unary unary && unary.operator() == TokenKind.BANG) {
      return narrowingsFor(unary.operand(), !truth);
    }
    if (condition instanceof Syntax.Binary binary) {
      if ((binary.operator() == TokenKind.AND_AND && truth)
          || (binary.operator() == TokenKind.OR_OR && !truth)) {
        Map<String, SemanticType> result = new LinkedHashMap<>();
        result.putAll(narrowingsFor(binary.left(), truth));
        result.putAll(narrowingsFor(binary.right(), truth));
        return result;
      }
      if (binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL) {
        Syntax.Name name = nullComparedName(binary);
        boolean nonNull =
            (binary.operator() == TokenKind.BANG_EQUAL && truth)
                || (binary.operator() == TokenKind.EQUAL_EQUAL && !truth);
        if (name != null && nonNull) {
          FlowScopes.ScopedSymbol scoped = findScoped(name.value());
          if (scoped != null
              && analyzer.context.flowScopes.type(scoped).isNullable()
              && isFlowNarrowable(scoped.id())) {
            return Map.of(name.value(), analyzer.context.flowScopes.type(scoped).nonNullable());
          }
        }
      }
    }
    return Map.of();
  }

  static Syntax.Name nullComparedName(Syntax.Binary binary) {
    if (binary.left() instanceof Syntax.Name name && binary.right() instanceof Syntax.NullLiteral)
      return name;
    if (binary.right() instanceof Syntax.Name name && binary.left() instanceof Syntax.NullLiteral)
      return name;
    return null;
  }

  boolean isFlowNarrowable(SymbolId id) {
    Symbol symbol = analyzer.context.symbols.get(id);
    return symbol != null
        && (symbol.kind() == SymbolKind.LOCAL_VARIABLE || symbol.kind() == SymbolKind.PARAMETER);
  }

  void applyNarrowings(Map<String, SemanticType> narrowings) {
    for (Map.Entry<String, SemanticType> entry : narrowings.entrySet()) {
      FlowScopes.ScopedSymbol symbol = findScoped(entry.getKey());
      if (symbol != null) {
        analyzer.context.flowScopes.update(symbol, entry.getValue());
      }
    }
  }

  FlowScopes.FlowState analyzeBranch(
      List<Syntax.Statement> statements,
      Map<String, SemanticType> narrowings,
      FlowScopes.FlowState incoming) {
    replaceFlow(incoming);
    pushScope(scopeSpan(statements));
    applyNarrowings(narrowings);
    analyzer.analyzeStatements(statements);
    popScope();
    return analyzer.context.flowScopes.snapshot();
  }

  FlowScopes.FlowState mergeFlows(
      FlowScopes.FlowState incoming, FlowScopes.FlowState left, FlowScopes.FlowState right) {
    Map<SymbolId, SemanticType> result = new HashMap<>();
    Map<SymbolId, LexicalLifetime> lifetimes = new HashMap<>();
    for (Map.Entry<SymbolId, SemanticType> entry : incoming.types().entrySet()) {
      SemanticType leftType = left.types().getOrDefault(entry.getKey(), entry.getValue());
      SemanticType rightType = right.types().getOrDefault(entry.getKey(), entry.getValue());
      SemanticType merged = entry.getValue();
      if (leftType.equals(rightType)) {
        merged = leftType;
      } else if (leftType.nonNullable().equals(rightType.nonNullable())) {
        merged = leftType.nonNullable().nullable();
      }
      result.put(entry.getKey(), merged);
      LexicalLifetime incomingLifetime = incoming.referenceLifetimes().get(entry.getKey());
      if (incomingLifetime != null) {
        LexicalLifetime leftLifetime =
            left.referenceLifetimes().getOrDefault(entry.getKey(), incomingLifetime);
        LexicalLifetime rightLifetime =
            right.referenceLifetimes().getOrDefault(entry.getKey(), incomingLifetime);
        lifetimes.put(entry.getKey(), leftLifetime.narrowest(rightLifetime));
      }
    }
    return new FlowScopes.FlowState(result, lifetimes);
  }

  void replaceFlow(FlowScopes.FlowState values) {
    analyzer.context.flowScopes.replace(values);
  }

  void validateContinue(SourceSpan span) {
    if (analyzer.context.controls.stream()
        .noneMatch(context -> context.kind() == ControlKind.LOOP)) {
      analyzer.context.diagnostics.error(
          INVALID_CONTROL, "continue is only valid inside for", span);
    }
  }

  void pushScope(SourceSpan span) {
    analyzer.context.flowScopes.push(span);
  }

  void popScope() {
    analyzer.context.flowScopes.pop();
  }

  SourceSpan scopeSpan(List<Syntax.Statement> statements) {
    if (statements.isEmpty()) return analyzer.context.currentProgram.span();
    return statements.getFirst().span().cover(statements.getLast().span());
  }

  Symbol register(
      Object declaration,
      String name,
      SymbolKind kind,
      SemanticType type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters) {
    SymbolId id = SymbolId.source(nameSpan.source().id(), analyzer.context.nextSymbolId++);
    Symbol symbol =
        new Symbol(
            id,
            name,
            kind,
            type,
            Optional.of(nameSpan.location()),
            Optional.ofNullable(owner),
            typeParameters,
            parameters,
            "");
    analyzer.context.symbols.put(id, symbol);
    analyzer.context.declarationSymbols.put(declaration, id);
    analyzer.context.bindings.put(nameSpan, id);
    return symbol;
  }

  Symbol registerDeclaration(
      Object declaration,
      String name,
      SymbolKind kind,
      SemanticType type,
      SourceSpan nameSpan,
      SymbolId owner,
      List<TypeParameterInfo> typeParameters,
      List<ParameterInfo> parameters) {
    SymbolId id =
        owner == null
            ? SymbolId.authored(
                DeclarationIdentity.topLevel(analyzer.context.currentProgram, declaration).value())
            : SymbolId.authored(DeclarationIdentity.member(owner, kind, declaration, name));
    Symbol symbol =
        new Symbol(
            id,
            name,
            kind,
            type,
            Optional.of(nameSpan.location()),
            Optional.ofNullable(owner),
            typeParameters,
            parameters,
            "");
    analyzer.context.symbols.put(id, symbol);
    analyzer.context.declarationSymbols.put(declaration, id);
    analyzer.context.bindings.put(nameSpan, id);
    return symbol;
  }

  List<TypeParameterInfo> symbolTypeParameters(
      List<Syntax.TypeParameter> parameters, Map<String, SemanticType> types) {
    return parameters.stream()
        .map(
            parameter -> {
              SemanticType type = types.get(parameter.name());
              Optional<SemanticType> bound =
                  parameter.upperBound().map(value -> resolveType(value, types));
              return new TypeParameterInfo(parameter.name(), type, bound);
            })
        .toList();
  }

  void registerTypeParameters(
      List<Syntax.TypeParameter> parameters, SymbolId owner, Map<String, SemanticType> types) {
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      SymbolId id = SymbolId.authored(DeclarationIdentity.typeParameter(owner, index));
      Symbol symbol =
          new Symbol(
              id,
              parameter.name(),
              SymbolKind.TYPE_PARAMETER,
              types.get(parameter.name()),
              Optional.of(parameter.nameSpan().location()),
              Optional.of(owner),
              List.of(),
              List.of(),
              "");
      analyzer.context.symbols.put(id, symbol);
      analyzer.context.declarationSymbols.put(parameter, id);
      analyzer.context.bindings.put(parameter.nameSpan(), id);
    }
  }

  Map<String, SymbolId> typeParameterSymbols(
      Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    if (owner != null) {
      owner
          .typeParameters()
          .forEach(
              parameter ->
                  result.put(parameter.name(), analyzer.context.declarationSymbols.get(parameter)));
    }
    function
        .typeParameters()
        .forEach(
            parameter ->
                result.put(parameter.name(), analyzer.context.declarationSymbols.get(parameter)));
    return Map.copyOf(result);
  }

  Map<String, SymbolId> typeParameterSymbols(List<Syntax.TypeParameter> parameters) {
    Map<String, SymbolId> result = new LinkedHashMap<>();
    parameters.forEach(
        parameter ->
            result.put(parameter.name(), analyzer.context.declarationSymbols.get(parameter)));
    return Map.copyOf(result);
  }

  void addMember(SymbolId owner, SymbolId member) {
    analyzer.context.members.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(member);
  }

  Optional<Symbol> typeSymbol(String name) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null)
      return Optional.ofNullable(
          analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(interfaceDecl)));
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl != null)
      return Optional.ofNullable(
          analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(aggregateDecl)));
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null)
      return Optional.ofNullable(
          analyzer.context.symbols.get(analyzer.context.declarationSymbols.get(enumDecl)));
    return analyzer.context.builtins.type(name);
  }

  List<ParameterInfo> parameters(List<Syntax.Parameter> parameters) {
    return parameters(parameters, Map.of(), analyzer.context.activeTypeParameters);
  }

  List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters, Map<String, SemanticType> substitutions) {
    return parameters(parameters, substitutions, analyzer.context.activeTypeParameters);
  }

  List<ParameterInfo> parameters(
      List<Syntax.Parameter> parameters,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return parameters.stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveType(parameter.type(), declarationTypes).substitute(substitutions),
                    parameter.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> parametersOf(
      Syntax.FunctionDecl function, Map<String, SemanticType> substitutions) {
    return function.parameters().stream()
        .map(
            parameter ->
                new ParameterInfo(
                    parameter.name(),
                    resolveDeclarationType(
                            parameter.type(), function, typeParameters(function, ownerOf(function)))
                        .substitute(substitutions),
                    parameter.defaultValue().isPresent()))
        .toList();
  }

  static boolean definitelyExits(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement
          || statement instanceof Syntax.ThrowStatement) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyExits(conditional.thenBody())
          && definitelyExits(conditional.elseBody())) {
        return true;
      }
      if (statement instanceof Syntax.TryStatement tried) {
        if (tried.finallyClause().isPresent()
            && definitelyExits(tried.finallyClause().orElseThrow().body())) {
          return true;
        }
        if (definitelyExits(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyExits(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields, Map<String, SemanticType> substitutions) {
    return fieldParameters(fields, substitutions, analyzer.context.activeTypeParameters);
  }

  List<ParameterInfo> fieldParameters(
      Syntax.AggregateDecl aggregateDecl, Map<String, SemanticType> substitutions) {
    return aggregateDecl.fields().stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveDeclarationType(
                            field.type(), field, aggregateTypeParameters(aggregateDecl))
                        .substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }

  List<ParameterInfo> fieldParameters(
      List<Syntax.FieldDecl> fields,
      Map<String, SemanticType> substitutions,
      Map<String, SemanticType> declarationTypes) {
    return fields.stream()
        .map(
            field ->
                new ParameterInfo(
                    field.name(),
                    resolveType(field.type(), declarationTypes).substitute(substitutions),
                    field.defaultValue().isPresent()))
        .toList();
  }

  SemanticType appliedType(String name, List<Syntax.TypeRef> arguments, SourceSpan span) {
    int arity = declaredTypeArity(name);
    if (arity < 0) return sourceType(name, List.of());
    if (arity != arguments.size()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "type '" + name + "' requires " + arity + " type argument(s), found " + arguments.size(),
          span);
    }
    List<SemanticType> resolved =
        arguments.stream()
            .map(argument -> resolveCheckedType(argument, analyzer.context.activeTypeParameters))
            .toList();
    if (analyzer.context.builtins.isType(name))
      return analyzer.context.builtins.instantiate(name, resolved);
    return sourceType(name, resolved);
  }

  SemanticType constructedType(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    if (!name.diamond()) return appliedType(name.value(), name.typeArguments(), name.span());
    String typeName = name.value();
    Symbol builtin = analyzer.context.builtins.type(typeName).orElse(null);
    Syntax.AggregateDecl source = resolveAggregate(typeName);
    List<TypeParameterInfo> parameters;
    SemanticType prototype;
    List<ParameterInfo> constructorParameters;
    if (builtin != null) {
      parameters = builtin.typeParameters();
      prototype =
          analyzer.context.builtins.instantiate(
              typeName, parameters.stream().map(TypeParameterInfo::type).toList());
      constructorParameters =
          analyzer.context.builtins.constructorParameters(prototype).orElse(List.of());
    } else if (source != null) {
      Map<String, SemanticType> declared = aggregateTypeParameters(source);
      parameters =
          source.typeParameters().stream()
              .map(
                  parameter ->
                      new TypeParameterInfo(parameter.name(), declared.get(parameter.name())))
              .toList();
      prototype =
          sourceType(source.name(), parameters.stream().map(TypeParameterInfo::type).toList());
      constructorParameters =
          source.constructors().isEmpty()
              ? fieldParameters(source, Map.of())
              : parameters(
                  source.constructors().getFirst().parameters(),
                  Map.of(),
                  aggregateTypeParameters(source));
    } else {
      analyzer.context.diagnostics.error(
          UNKNOWN_NAME, "cannot find type '" + typeName + "'", name.span());
      return SemanticType.DYNAMIC;
    }
    if (parameters.isEmpty()) {
      analyzer.context.diagnostics.error(
          INVALID_CALL, "diamond requires a generic type constructor", name.span());
      return prototype;
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(parameters.stream().map(TypeParameterInfo::type).toList());
    if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
      constrainInference(solver, prototype, expected);
    }
    Map<String, SemanticType> contextualSubstitutions = solver.solve().substitutions();
    List<Integer> indices =
        analyzer.context.overloads.argumentIndices(call, constructorParameters, false);
    if (indices != null) {
      for (int index = 0; index < call.arguments().size(); index++) {
        Syntax.Expression argument = call.arguments().get(index).value();
        SemanticType inferencePattern = constructorParameters.get(indices.get(index)).type();
        SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
        TypeProbe probe =
            analyzer.probeType(
                argument,
                containsTypeParameter(pattern, solverVariables(parameters)) ? null : pattern);
        constrainInference(solver, inferencePattern, probe.type());
      }
    }
    TypeConstraintSolver.Solution solution = solver.solve();
    Map<String, String> parameterNames =
        parameters.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(),
                    TypeParameterInfo::name,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (String missing : solution.missing()) {
      analyzer.context.diagnostics.error(
          INVALID_CALL,
          "cannot infer type argument '" + parameterNames.get(missing) + "'",
          name.span());
    }
    for (TypeConstraintSolver.Conflict conflict : solution.conflicts()) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + parameterNames.get(conflict.variable())
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          name.span());
    }
    List<SemanticType> arguments =
        parameters.stream()
            .map(
                parameter ->
                    solution
                        .substitutions()
                        .getOrDefault(parameter.type().identity(), SemanticType.DYNAMIC))
            .toList();
    return builtin != null
        ? analyzer.context.builtins.instantiate(typeName, arguments)
        : sourceType(typeName, arguments);
  }

  static Set<String> solverVariables(List<TypeParameterInfo> parameters) {
    return parameters.stream()
        .map(parameter -> parameter.type().identity())
        .collect(java.util.stream.Collectors.toSet());
  }

  SemanticType resolveType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    if (type.isWildcard()) return SemanticType.EXISTENTIAL;
    if (type.name().equals("ref")) {
      if (type.arguments().size() != 1) return SemanticType.DYNAMIC;
      SemanticType target = resolveType(type.arguments().getFirst(), typeParameters);
      return target.containsReference()
              || target.isNullable()
              || target.category() != ValueCategory.VALUE
          ? SemanticType.DYNAMIC
          : SemanticType.reference(target);
    }
    SemanticType parameter = typeParameters.get(type.name());
    if (parameter != null) {
      if (parameter.containsReference()) return SemanticType.DYNAMIC;
      return type.nullable() ? parameter.nullable() : parameter;
    }
    if (type.name().equals("Void")) {
      return type.nullable() ? SemanticType.DYNAMIC : SemanticType.VOID;
    }
    List<SemanticType> arguments =
        type.arguments().stream().map(argument -> resolveType(argument, typeParameters)).toList();
    if (arguments.stream().anyMatch(SemanticType::containsReference)) {
      return SemanticType.DYNAMIC;
    }
    if (type.name().equals("Function") && !arguments.isEmpty()) {
      SemanticType function =
          SemanticType.function(arguments.getFirst(), arguments.subList(1, arguments.size()));
      return type.nullable() ? function.nullable() : function;
    }
    SemanticType resolved =
        analyzer.context.builtins.isType(type.name())
            ? analyzer.context.builtins.instantiate(type.name(), arguments)
            : sourceType(type.name(), arguments);
    return type.nullable() ? resolved.nullable() : resolved;
  }

  SemanticType resolveCheckedType(Syntax.TypeRef type, Map<String, SemanticType> typeParameters) {
    validateType(type, false);
    SemanticType resolved = resolveType(type, typeParameters);
    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
  }

  SemanticType resolveDeclarationType(
      Syntax.TypeRef type, Object declaration, Map<String, SemanticType> typeParameters) {
    Syntax.Program previous = analyzer.context.currentProgram;
    analyzer.context.currentProgram = analyzer.context.declarations.ownerOr(declaration, previous);
    try {
      return resolveType(type, typeParameters);
    } finally {
      analyzer.context.currentProgram = previous;
    }
  }

  void validatePublicType(Syntax.TypeRef type) {
    if (type.isWildcard()) return;
    if (analyzer.context.activeTypeParameters.containsKey(type.name())) return;
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(type.name());
    Syntax.EnumDecl enumDecl = resolveEnum(type.name());
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(type.name());
    boolean privateType =
        interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
            || aggregateDecl != null && aggregateDecl.visibility() == Syntax.Visibility.PRIVATE
            || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE;
    if (privateType) {
      analyzer.context.diagnostics.error(
          TYPE_MISMATCH,
          "private type '" + type.name() + "' cannot appear in a public signature",
          type.span());
    }
    type.arguments().forEach(this::validatePublicType);
  }

  void validateTypeArgumentCount(
      String name, int expected, List<Syntax.TypeRef> arguments, SourceSpan span) {
    if (arguments.size() != expected) {
      analyzer.context.diagnostics.error(
          INVALID_CALL,
          "function '"
              + name
              + "' requires "
              + expected
              + " type argument(s), found "
              + arguments.size(),
          span);
    }
  }

  void bindDeclarationUse(SourceSpan span, String localName, Object declaration) {
    for (Syntax.ImportDecl imported : analyzer.context.currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        analyzer.context.bindings.put(span, analyzer.context.importAliases.get(imported));
        return;
      }
    }
    analyzer.context.bindings.put(span, analyzer.context.declarationSymbols.get(declaration));
  }

  SymbolId importedAlias(String localName) {
    if (analyzer.context.currentProgram == null) return null;
    for (Syntax.ImportDecl imported : analyzer.context.currentProgram.imports()) {
      if (imported.alias().isPresent() && imported.localName().equals(localName)) {
        return analyzer.context.importAliases.get(imported);
      }
    }
    return null;
  }

  SemanticType sourceType(String name, List<SemanticType> arguments) {
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl == null) aggregateDecl = resolveImportedAggregateByDeclaredName(name);
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    Object declaration =
        interfaceDecl != null ? interfaceDecl : aggregateDecl != null ? aggregateDecl : enumDecl;
    Syntax.Program owner =
        declaration == null
            ? analyzer.context.currentProgram
            : analyzer.context.declarations.owner(declaration);
    String declaredName =
        interfaceDecl != null
            ? interfaceDecl.name()
            : aggregateDecl != null
                ? aggregateDecl.name()
                : enumDecl != null ? enumDecl.name() : name;
    String identity = qualifiedName(owner == null ? "" : owner.packageName(), declaredName);
    if (interfaceDecl != null && interfaceDecl.visibility() == Syntax.Visibility.PRIVATE
        || aggregateDecl != null && aggregateDecl.visibility() == Syntax.Visibility.PRIVATE
        || enumDecl != null && enumDecl.visibility() == Syntax.Visibility.PRIVATE) {
      identity = fileLocalIdentity(identity, owner);
    }
    ValueCategory category =
        interfaceDecl != null
            ? ValueCategory.POLYMORPHIC
            : aggregateDecl != null
                ? aggregateDecl.kind() != Syntax.AggregateKind.VALUE
                    ? ValueCategory.IDENTITY
                    : ValueCategory.VALUE
                : ValueCategory.VALUE;
    return SemanticType.declared(identity, declaredName, arguments, category);
  }

  int declaredTypeArity(String name) {
    int builtinArity = analyzer.context.builtins.typeArity(name);
    if (builtinArity >= 0) return builtinArity;
    Syntax.InterfaceDecl interfaceDecl = resolveInterface(name);
    if (interfaceDecl != null) return interfaceDecl.typeParameters().size();
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(name);
    if (aggregateDecl != null) return aggregateDecl.typeParameters().size();
    Syntax.EnumDecl enumDecl = resolveEnum(name);
    if (enumDecl != null) return enumDecl.typeParameters().size();
    return resolveAnnotation(name) == null ? -1 : 0;
  }

  Map<String, SemanticType> typeParameters(
      Syntax.FunctionDecl function, Syntax.AggregateDecl owner) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    if (owner != null) result.putAll(aggregateTypeParameters(owner));
    result.putAll(functionTypeParameters(function));
    return Map.copyOf(result);
  }

  Map<String, SemanticType> aggregateTypeParameters(Syntax.AggregateDecl aggregateDecl) {
    return declarationTypeParameters(
        analyzer.context.declarations.ownerOr(aggregateDecl, analyzer.context.currentProgram),
        "aggregate/" + aggregateDecl.name(),
        aggregateDecl.typeParameters());
  }

  static String aggregateKeyword(Syntax.AggregateDecl declaration) {
    return declaration.kind().keyword();
  }

  record AggregateView(Syntax.AggregateDecl declaration, SemanticType type) {}

  AggregateField aggregateField(SemanticType receiver, String name) {
    for (AggregateView view : aggregateViews(receiver)) {
      Syntax.FieldDecl field =
          view.declaration().fields().stream()
              .filter(candidate -> candidate.name().equals(name))
              .findFirst()
              .orElse(null);
      if (field != null) return new AggregateField(field, view);
    }
    return null;
  }

  record AggregateField(Syntax.FieldDecl field, AggregateView view) {}

  Map<String, SemanticType> interfaceTypeParameters(Syntax.InterfaceDecl declaration) {
    return declarationTypeParameters(
        analyzer.context.declarations.ownerOr(declaration, analyzer.context.currentProgram),
        "interface/" + declaration.name(),
        declaration.typeParameters());
  }

  SemanticType interfaceSelfType(Syntax.InterfaceDecl declaration) {
    Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
    return sourceType(
        declaration.name(),
        declaration.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> withTypeParameters(
      Map<String, SemanticType> base,
      List<Syntax.TypeParameter> parameters,
      Syntax.Program program,
      String owner) {
    Map<String, SemanticType> result = new LinkedHashMap<>(base);
    result.putAll(declarationTypeParameters(program, owner, parameters));
    return Map.copyOf(result);
  }

  SemanticType aggregateSelfType(Syntax.AggregateDecl aggregateDecl) {
    Map<String, SemanticType> parameters = aggregateTypeParameters(aggregateDecl);
    return sourceType(
        aggregateDecl.name(),
        aggregateDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> enumTypeParameters(Syntax.EnumDecl enumDecl) {
    return declarationTypeParameters(
        analyzer.context.declarations.ownerOr(enumDecl, analyzer.context.currentProgram),
        "enum/" + enumDecl.name(),
        enumDecl.typeParameters());
  }

  SemanticType enumSelfType(Syntax.EnumDecl enumDecl) {
    Map<String, SemanticType> parameters = enumTypeParameters(enumDecl);
    return sourceType(
        enumDecl.name(),
        enumDecl.typeParameters().stream()
            .map(parameter -> parameters.get(parameter.name()))
            .toList());
  }

  Map<String, SemanticType> functionTypeParameters(Syntax.FunctionDecl function) {
    return declarationTypeParameters(
        analyzer.context.declarations.ownerOr(function, analyzer.context.currentProgram),
        "function/" + function.name(),
        function.typeParameters());
  }

  Map<String, SemanticType> declarationTypeParameters(
      Syntax.Program program, String owner, List<Syntax.TypeParameter> parameters) {
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0; index < parameters.size(); index++) {
      Syntax.TypeParameter parameter = parameters.get(index);
      result.putIfAbsent(
          parameter.name(),
          SemanticType.parameter(
              program.span().source().id() + "/" + owner + "/" + index, parameter.name()));
    }
    return Map.copyOf(result);
  }

  void validateTypeParameterNames(List<Syntax.TypeParameter> parameters) {
    Set<String> names = new HashSet<>();
    for (Syntax.TypeParameter parameter : parameters) {
      if (!names.add(parameter.name())) {
        analyzer.context.diagnostics.error(
            DUPLICATE_NAME,
            "type parameter '" + parameter.name() + "' is already declared",
            parameter.nameSpan());
      }
    }
  }

  Map<String, SemanticType> aggregateSubstitutions(
      Syntax.AggregateDecl aggregateDecl, SemanticType instance) {
    Map<String, SemanticType> parameters = aggregateTypeParameters(aggregateDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(aggregateDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameters.get(aggregateDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  Map<String, SemanticType> enumSubstitutions(Syntax.EnumDecl enumDecl, SemanticType instance) {
    Map<String, SemanticType> parameters = enumTypeParameters(enumDecl);
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(enumDecl.typeParameters().size(), instance.arguments().size());
        index++) {
      SemanticType parameter = parameters.get(enumDecl.typeParameters().get(index).name());
      result.put(parameter.identity(), instance.arguments().get(index));
    }
    return result;
  }

  Map<String, SemanticType> inferBuiltinTypeArguments(
      Symbol symbol,
      List<Syntax.TypeRef> explicitArguments,
      Syntax.Call call,
      SemanticType expected,
      SourceSpan span) {
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    if (!explicitArguments.isEmpty()) {
      validateTypeArgumentCount(
          symbol.name(), symbol.typeParameters().size(), explicitArguments, span);
      for (int index = 0;
          index < Math.min(explicitArguments.size(), symbol.typeParameters().size());
          index++) {
        SemanticType parameter = symbol.typeParameters().get(index).type();
        if (parameter != null) {
          substitutions.put(
              parameter.identity(),
              resolveCheckedType(
                  explicitArguments.get(index), analyzer.context.activeTypeParameters));
        }
      }
      for (int index = symbol.typeParameters().size(); index < explicitArguments.size(); index++) {
        resolveCheckedType(explicitArguments.get(index), analyzer.context.activeTypeParameters);
      }
    } else {
      TypeConstraintSolver solver =
          new TypeConstraintSolver(
              symbol.typeParameters().stream().map(TypeParameterInfo::type).toList());
      Set<String> variables = solverVariables(symbol.typeParameters());
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        constrainInference(solver, symbol.type(), expected);
      }
      Map<String, SemanticType> contextualSubstitutions = solver.solve().substitutions();
      List<Integer> indices =
          analyzer.context.overloads.argumentIndices(call, symbol.parameters(), false);
      if (indices != null) {
        for (int index = 0; index < call.arguments().size(); index++) {
          Syntax.CallArgument argument = call.arguments().get(index);
          SemanticType inferencePattern = symbol.parameters().get(indices.get(index)).type();
          SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
          SemanticType argumentExpected =
              containsTypeParameter(pattern, variables)
                      && !(argument.value() instanceof Syntax.Lambda && pattern.isFunction())
                  ? null
                  : pattern;
          constrainInference(
              solver, inferencePattern, analyzer.typeOf(argument.value(), argumentExpected));
        }
      }
      TypeConstraintSolver.Solution solution = solver.solve();
      substitutions.putAll(solution.substitutions());
      Map<String, String> parameterNames =
          symbol.typeParameters().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      parameter -> parameter.type().identity(),
                      TypeParameterInfo::name,
                      (left, right) -> left,
                      LinkedHashMap::new));
      for (TypeConstraintSolver.Conflict conflict : solution.conflicts()) {
        analyzer.context.diagnostics.error(
            TYPE_MISMATCH,
            "type parameter '"
                + parameterNames.get(conflict.variable())
                + "' inferred as both "
                + conflict.first().displayName()
                + " and "
                + conflict.second().displayName(),
            span);
      }
    }
    for (TypeParameterInfo parameterInfo : symbol.typeParameters()) {
      String name = parameterInfo.name();
      SemanticType parameter = parameterInfo.type();
      if (parameter != null && !substitutions.containsKey(parameter.identity())) {
        analyzer.context.diagnostics.error(
            INVALID_CALL, "cannot infer type argument '" + name + "'", span);
        substitutions.put(parameter.identity(), SemanticType.DYNAMIC);
      }
    }
    return substitutions;
  }

  static SemanticType findTypeParameter(SemanticType type, String name) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER && type.name().equals(name)) return type;
    for (SemanticType argument : type.arguments()) {
      SemanticType result = findTypeParameter(argument, name);
      if (result != null) return result;
    }
    return null;
  }

  Syntax.FunctionDecl resolveFunction(String name) {
    return analyzer.context.declarations.resolveFunction(analyzer.context.currentProgram, name);
  }

  List<Syntax.FunctionDecl> resolveFunctions(String name) {
    return analyzer.context.declarations.resolveFunctions(analyzer.context.currentProgram, name);
  }

  Map<SymbolId, List<SymbolId>> callableGroups() {
    Map<SymbolId, List<SymbolId>> result = new LinkedHashMap<>();
    for (List<Syntax.FunctionDecl> group : analyzer.context.declarations.functionGroups()) {
      List<SymbolId> ids = group.stream().map(analyzer.context.declarationSymbols::get).toList();
      ids.forEach(id -> result.put(id, ids));
    }
    return Map.copyOf(result);
  }

  Map<String, List<SemanticType>> interfaceParentTypes() {
    Map<String, List<SemanticType>> result = new LinkedHashMap<>();
    Syntax.Program previous = analyzer.context.currentProgram;
    for (Syntax.InterfaceDecl declaration : analyzer.context.declarations.interfaces()) {
      analyzer.context.currentProgram = analyzer.context.declarations.owner(declaration);
      Map<String, SemanticType> parameters = interfaceTypeParameters(declaration);
      String identity =
          analyzer
              .context
              .symbols
              .get(analyzer.context.declarationSymbols.get(declaration))
              .type()
              .identity();
      result.put(
          identity,
          declaration.extendedInterfaces().stream()
              .map(parent -> resolveType(parent, parameters))
              .toList());
    }
    analyzer.context.currentProgram = previous;
    return Map.copyOf(result);
  }

  Syntax.AggregateDecl resolveAggregate(String name) {
    return analyzer.context.declarations.resolveAggregate(analyzer.context.currentProgram, name);
  }

  Syntax.AggregateDecl resolveImportedAggregateByDeclaredName(String name) {
    return analyzer.context.declarations.importedAggregateByDeclaredName(
        analyzer.context.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAggregate(SemanticType type) {
    return analyzer.context.declarations.resolveAggregate(type);
  }

  Syntax.AggregateDecl ownerOf(Syntax.FunctionDecl method) {
    return analyzer.context.declarations.ownerOf(method);
  }

  Syntax.EnumDecl resolveEnum(String name) {
    return analyzer.context.declarations.resolveEnum(analyzer.context.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAnnotation(String name) {
    return analyzer.context.declarations.resolveAnnotation(analyzer.context.currentProgram, name);
  }

  Syntax.AggregateDecl resolveAnnotation(SemanticType type) {
    return analyzer.context.declarations.resolveAnnotation(type);
  }

  Syntax.InterfaceDecl resolveInterface(String name) {
    return analyzer.context.declarations.resolveInterface(analyzer.context.currentProgram, name);
  }

  Syntax.InterfaceDecl resolveInterface(SemanticType type) {
    return analyzer.context.declarations.resolveInterface(type);
  }

  Syntax.EnumDecl resolveEnum(SemanticType type) {
    return analyzer.context.declarations.resolveEnum(type);
  }

  boolean canImport(Syntax.Program importer, Object declaration) {
    return analyzer.context.declarations.canImport(importer, declaration);
  }

  static String callableSignature(Syntax.FunctionDecl function) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < function.typeParameters().size(); index++) {
      typeParameters.put(function.typeParameters().get(index).name(), "$" + index);
    }
    return function.name()
        + "("
        + function.parameters().stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  static String constructorSignature(Syntax.ConstructorDecl constructor) {
    return constructor.parameters().stream()
        .map(parameter -> normalizedType(parameter.type(), Map.of()))
        .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  static String fileLocalIdentity(String qualified, Syntax.Program program) {
    return qualified + "@" + program.span().source().id().uri();
  }

  static String interfaceMethodSignature(Syntax.InterfaceMethodDecl method) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < method.typeParameters().size(); index++) {
      typeParameters.put(method.typeParameters().get(index).name(), "$" + index);
    }
    return method.name()
        + "("
        + method.parameters().stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  static String normalizedType(Syntax.TypeRef type, Map<String, String> typeParameters) {
    if (type.isWildcard()) return "?";
    String name = typeParameters.getOrDefault(type.name(), type.name());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> normalizedType(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return name + arguments + (type.nullable() ? "?" : "");
  }

  static String qualifiedName(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }
}
