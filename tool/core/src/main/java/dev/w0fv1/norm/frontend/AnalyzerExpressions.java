package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.PatternCoverage;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.LexicalLifetime;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

abstract class AnalyzerExpressions extends AnalyzerTypeSystem {
  AnalyzerExpressions(
      List<Syntax.Program> programs,
      Syntax.Program entryProgram,
      DiagnosticBag diagnostics,
      boolean requireEntryPoint,
      Set<DocumentId> exportedSources,
      CompilationGuard guard,
      Map<SourceSpan, SemanticContribution> reusableDeclarations,
      int minimumBodySymbolId,
      Set<DocumentId> moduleEvaluationDocuments,
      CompilationScope scope) {
    super(
        programs,
        entryProgram,
        diagnostics,
        requireEntryPoint,
        exportedSources,
        guard,
        reusableDeclarations,
        minimumBodySymbolId,
        moduleEvaluationDocuments,
        scope);
  }

  abstract void analyzeStatement(Syntax.Statement statement);

  @Override
  final SemanticType typeOf(Syntax.Expression expression, SemanticType expected) {
    guard.checkpoint();
    SemanticType type =
        switch (expression) {
          case Syntax.IntegerLiteral integer ->
              numericIntegerType(integer.value(), expected, integer.span());
          case Syntax.DecimalLiteral decimal ->
              numericDecimalType(decimal.value(), expected, decimal.span());
          case Syntax.CodePointLiteral ignored -> SemanticType.CODE_POINT;
          case Syntax.BooleanLiteral ignored -> SemanticType.BOOLEAN;
          case Syntax.NullLiteral literal -> analyzeNull(literal, expected);
          case Syntax.StringLiteralExpr ignored -> SemanticType.STRING;
          case Syntax.ArrayLiteral array -> analyzeArray(array, expected);
          case Syntax.Name name -> analyzeNameValue(name, expected);
          case Syntax.Unary unary -> analyzeUnary(unary, expected);
          case Syntax.Binary binary -> analyzeBinary(binary, expected);
          case Syntax.Call call -> analyzeCall(call, expected);
          case Syntax.Member member -> memberType(member);
          case Syntax.Lambda lambda -> analyzeLambda(lambda, expected);
          case Syntax.MethodReference reference -> analyzeMethodReference(reference, expected);
          case Syntax.Index index -> analyzeIndex(index);
          case Syntax.SwitchExpression switchExpression ->
              analyzeSwitch(switchExpression, expected);
        };
    if (expected != null && type.equals(SemanticType.DYNAMIC)) {
      type = expected;
    }
    semanticTypes.put(expression.span(), type);
    if (type.isReference()) {
      referenceLifetimes.put(expression.span(), referenceLifetime(expression));
    }
    return type;
  }

  LexicalLifetime referenceLifetime(Syntax.Expression expression) {
    LexicalLifetime known = referenceLifetimes.get(expression.span());
    if (known != null) return known;
    if (expression instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol symbol = findScoped(name.value());
      LexicalLifetime lifetime = symbol == null ? null : flowScopes.referenceLifetime(symbol);
      return lifetime == null ? LexicalLifetime.unusable() : lifetime;
    }
    if (expression instanceof Syntax.Unary unary && unary.operator() == TokenKind.AMPERSAND) {
      if (unary.operand() instanceof Syntax.Name name) {
        FlowScopes.ScopedSymbol symbol = findScoped(name.value());
        if (symbol != null) {
          SymbolKind kind = scopedSymbol(symbol).kind();
          if (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER) {
            return flowScopes.storageLifetime(symbol);
          }
          if (kind == SymbolKind.FIELD) return LexicalLifetime.longLived();
        }
      }
      if (unary.operand() instanceof Syntax.Member) {
        return LexicalLifetime.longLived();
      }
    }
    return LexicalLifetime.unusable();
  }

  SemanticType analyzeNameValue(Syntax.Name name, SemanticType expected) {
    FlowScopes.ScopedSymbol scoped = findScoped(name.value());
    if (scoped != null) {
      Symbol symbol = scopedSymbol(scoped);
      if (!lambdaLocals.isEmpty()
          && !lambdaLocals.getFirst().contains(scoped.id())
          && (symbol.kind() == SymbolKind.LOCAL_VARIABLE
              || symbol.kind() == SymbolKind.PARAMETER
              || symbol.kind() == SymbolKind.SELF)) {
        if (symbol.type().isReference()) {
          diagnostics.error(TYPE_MISMATCH, "ref cannot be captured by a lambda", name.span());
        }
        capturedLocals.add(scoped.id());
        if (assignedLocals.contains(scoped.id())) reportMutableCapture(scoped.id(), name.span());
      }
      return lookup(name.value(), name.span());
    }
    List<Syntax.FunctionDecl> candidates = resolveFunctions(name.value());
    if (!candidates.isEmpty()) {
      if (expected == null || !expected.isFunction()) {
        diagnostics.error(
            TYPE_MISMATCH,
            "function reference '" + name.value() + "' requires an expected function type",
            name.span());
        return SemanticType.DYNAMIC;
      }
      List<FunctionReferenceResolution> matches =
          candidates.stream()
              .map(
                  candidate ->
                      resolveFunctionReference(candidate, functionType(candidate), expected))
              .flatMap(Optional::stream)
              .toList();
      if (matches.size() != 1) {
        diagnostics.error(
            TYPE_MISMATCH,
            matches.isEmpty()
                ? "no overload of '" + name.value() + "' matches " + expected.displayName()
                : "function reference '"
                    + name.value()
                    + "' is ambiguous for "
                    + expected.displayName(),
            name.span());
        return SemanticType.DYNAMIC;
      }
      FunctionReferenceResolution resolution = matches.getFirst();
      Syntax.FunctionDecl selected = resolution.declaration();
      bindDeclarationUse(name.span(), name.value(), selected);
      functionReferenceTypeArguments.put(name.span(), resolution.reifiedArguments());
      return expected.nonNullable();
    }
    return lookup(name.value(), name.span());
  }

  SemanticType functionType(Syntax.FunctionDecl declaration) {
    Map<String, SemanticType> parameters = functionTypeParameters(declaration);
    return SemanticType.function(
        functionReturnType(declaration, parameters),
        declaration.parameters().stream()
            .map(parameter -> resolveDeclarationType(parameter.type(), declaration, parameters))
            .toList());
  }

  @Override
  final SemanticType functionReturnType(
      Syntax.FunctionDecl declaration, Map<String, SemanticType> typeParameters) {
    return declaration
        .returnType()
        .map(type -> resolveDeclarationType(type, declaration, typeParameters))
        .orElseGet(
            () -> {
              Syntax.AggregateDecl owner = ownerOf(declaration);
              return owner == null ? SemanticType.VOID : aggregateSelfType(owner);
            });
  }

  SemanticType analyzeLambda(Syntax.Lambda lambda, SemanticType expected) {
    SemanticType expectedFunction = expected != null && expected.isFunction() ? expected : null;
    if (expectedFunction != null
        && expectedFunction.functionParameterTypes().size() != lambda.parameters().size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "lambda requires "
              + expectedFunction.functionParameterTypes().size()
              + " parameter(s), found "
              + lambda.parameters().size(),
          lambda.span());
      expectedFunction = null;
    }
    List<SemanticType> parameterTypes = new ArrayList<>();
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      SemanticType contextual =
          expectedFunction == null ? null : expectedFunction.functionParameterTypes().get(index);
      SemanticType explicit =
          parameter
              .type()
              .map(
                  type -> {
                    validateType(type, false);
                    SemanticType resolved = resolveType(type, activeTypeParameters);
                    return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                  })
              .orElse(null);
      if (explicit != null && contextual != null)
        requireType(contextual, explicit, parameter.span());
      SemanticType resolved = explicit != null ? explicit : contextual;
      if (resolved == null) {
        diagnostics.error(TYPE_MISMATCH, "cannot infer lambda parameter type", parameter.span());
        resolved = SemanticType.DYNAMIC;
      }
      parameterTypes.add(resolved);
    }
    SemanticType previousReturn = expectedReturnType;
    boolean previousImplicitSelfReturn = implicitSelfReturn;
    SemanticType declaredContextualReturn =
        lambda
            .returnType()
            .map(
                type -> {
                  validateType(type, true);
                  SemanticType resolved = resolveType(type, activeTypeParameters);
                  return resolved.containsReference() ? SemanticType.DYNAMIC : resolved;
                })
            .orElse(expectedFunction == null ? null : expectedFunction.functionReturnType());
    SemanticType contextualReturn =
        declaredContextualReturn != null
                && declaredContextualReturn.kind() == SemanticType.Kind.TYPE_PARAMETER
            ? null
            : declaredContextualReturn;
    if (lambda.returnType().isPresent() && expectedFunction != null) {
      requireType(
          expectedFunction.functionReturnType(),
          declaredContextualReturn,
          lambda.returnType().orElseThrow().span());
    }
    expectedReturnType = contextualReturn == null ? SemanticType.DYNAMIC : contextualReturn;
    implicitSelfReturn = false;
    pushScope(lambda.span());
    Deque<ControlContext> outerControls = new ArrayDeque<>(controls);
    controls.clear();
    Set<SymbolId> localSymbols = new HashSet<>();
    lambdaLocals.addFirst(localSymbols);
    for (int index = 0; index < lambda.parameters().size(); index++) {
      Syntax.LambdaParameter parameter = lambda.parameters().get(index);
      Symbol symbol =
          register(
              parameter,
              parameter.name(),
              SymbolKind.PARAMETER,
              parameterTypes.get(index),
              parameter.nameSpan(),
              currentCallable,
              List.of(),
              List.of());
      declareExisting(
          parameter.name(), parameterTypes.get(index), parameter.nameSpan(), symbol.id());
      localSymbols.add(symbol.id());
    }
    SemanticType result = contextualReturn;
    int last = lambda.body().size() - 1;
    for (int index = 0; index < lambda.body().size(); index++) {
      Syntax.Statement statement = lambda.body().get(index);
      if (index == last && statement instanceof Syntax.ExpressionStatement expression) {
        result = typeOf(expression.expression(), contextualReturn);
        if (contextualReturn != null)
          requireAssignable(contextualReturn, result, expression.span());
      } else {
        analyzeStatement(statement);
      }
    }
    controls.addAll(outerControls);
    popScope();
    lambdaLocals.removeFirst();
    expectedReturnType = previousReturn;
    implicitSelfReturn = previousImplicitSelfReturn;
    if (result == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "lambda return type requires an expected type or a final expression",
          lambda.span());
      result = SemanticType.DYNAMIC;
    }
    if (result.containsReference()) {
      diagnostics.error(TYPE_MISMATCH, "lambda return type cannot contain ref", lambda.span());
      result = SemanticType.DYNAMIC;
    }
    return SemanticType.function(result, parameterTypes);
  }

  Symbol scopedSymbol(FlowScopes.ScopedSymbol scoped) {
    return symbols.get(scoped.id());
  }

  void reportMutableCapture(SymbolId symbol, SourceSpan span) {
    if (reportedMutableCaptures.add(symbol)) {
      diagnostics.error(
          INVALID_CONTROL,
          "captured local '" + symbols.get(symbol).name() + "' must be effectively final",
          span);
    }
  }

  SemanticType analyzeMethodReference(Syntax.MethodReference reference, SemanticType expected) {
    SemanticType receiver = typeOf(reference.receiver(), null);
    if (expected == null || !expected.isFunction()) {
      diagnostics.error(
          TYPE_MISMATCH, "method reference requires an expected function type", reference.span());
      return SemanticType.DYNAMIC;
    }
    Syntax.AggregateDecl aggregate = resolveAggregate(receiver.nonNullable());
    if (aggregate == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type '" + receiver.displayName() + "' has no source methods",
          reference.span());
      return SemanticType.DYNAMIC;
    }
    List<FunctionReferenceResolution> matches = List.of();
    for (AggregateView view : aggregateViews(receiver.nonNullable())) {
      Map<String, SemanticType> substitutions =
          aggregateSubstitutions(view.declaration(), view.type());
      matches =
          view.declaration().methods().stream()
              .filter(method -> method.name().equals(reference.name()))
              .filter(
                  method ->
                      method.visibility() != Syntax.Visibility.PRIVATE
                          || currentAggregate == view.declaration())
              .map(
                  method -> {
                    Map<String, SemanticType> parameters =
                        typeParameters(method, view.declaration());
                    SemanticType pattern =
                        SemanticType.function(
                                functionReturnType(method, parameters),
                                method.parameters().stream()
                                    .map(
                                        parameter ->
                                            resolveDeclarationType(
                                                parameter.type(), method, parameters))
                                    .toList())
                            .substitute(substitutions);
                    return resolveFunctionReference(method, pattern, expected);
                  })
              .flatMap(Optional::stream)
              .toList();
      if (!matches.isEmpty()) break;
    }
    if (matches.size() != 1) {
      diagnostics.error(
          TYPE_MISMATCH,
          matches.isEmpty()
              ? "no method '" + reference.name() + "' matches " + expected.displayName()
              : "method reference '"
                  + reference.name()
                  + "' is ambiguous for "
                  + expected.displayName(),
          reference.span());
      return SemanticType.DYNAMIC;
    }
    FunctionReferenceResolution resolution = matches.getFirst();
    Syntax.FunctionDecl selected = resolution.declaration();
    bindings.put(reference.nameSpan(), declarationSymbols.get(selected));
    functionReferenceTypeArguments.put(reference.span(), resolution.reifiedArguments());
    return expected.nonNullable();
  }

  Optional<FunctionReferenceResolution> resolveFunctionReference(
      Syntax.FunctionDecl declaration, SemanticType pattern, SemanticType expected) {
    SemanticType target = expected.nonNullable();
    Symbol symbol = symbols.get(declarationSymbols.get(declaration));
    if (symbol.typeParameters().isEmpty()) {
      return pattern.equals(target)
          ? Optional.of(new FunctionReferenceResolution(declaration, List.of()))
          : Optional.empty();
    }
    TypeConstraintSolver solver =
        new TypeConstraintSolver(
            symbol.typeParameters().stream().map(TypeParameterInfo::type).toList());
    constrainInference(solver, pattern, target);
    TypeConstraintSolver.Solution solution = solver.solve();
    if (!solution.missing().isEmpty() || !solution.conflicts().isEmpty()) {
      return Optional.empty();
    }
    List<SemanticType> arguments =
        symbol.typeParameters().stream()
            .map(parameter -> solution.substitutions().get(parameter.type().identity()))
            .toList();
    if (arguments.stream().anyMatch(java.util.Objects::isNull)) return Optional.empty();
    for (int index = 0; index < symbol.typeParameters().size(); index++) {
      TypeParameterInfo parameter = symbol.typeParameters().get(index);
      SemanticType bound =
          parameter
              .upperBound()
              .map(value -> value.substitute(solution.substitutions()))
              .orElse(null);
      if (bound != null && !isAssignable(bound, arguments.get(index))) return Optional.empty();
    }
    return pattern.substitute(solution.substitutions()).equals(target)
        ? Optional.of(new FunctionReferenceResolution(declaration, arguments))
        : Optional.empty();
  }

  SemanticType analyzeSwitch(Syntax.SwitchExpression switchExpression, SemanticType expected) {
    SemanticType valueType = typeOf(switchExpression.value(), null);
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    PatternCoverage<SemanticType> coverage = new PatternCoverage<>(new SemanticPatternDomain());
    ControlContext context = ControlContext.switchExpression(expected);
    FlowScopes.FlowState incoming = flowScopes.snapshot();
    List<FlowScopes.FlowState> caseFlows = new ArrayList<>();
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      replaceFlow(incoming);
      pushScope(switchCase.span());
      PatternCoverage.Pattern pattern = analyzePattern(switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        diagnostics.error(
            INVALID_CONTROL, "switch case is unreachable", switchCase.pattern().span());
      }
      previous.add(pattern);
      controls.addFirst(context);
      analyzeStatements(switchCase.body());
      controls.removeFirst();
      popScope();
      caseFlows.add(flowScopes.snapshot());
    }
    if (!caseFlows.isEmpty()) {
      FlowScopes.FlowState merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = mergeFlows(incoming, merged, caseFlows.get(index));
      }
      replaceFlow(merged);
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      diagnostics.error(INVALID_CONTROL, "switch is not exhaustive", switchExpression.span());
    }
    SemanticType result = context.resultType();
    if (result == null) return SemanticType.VOID;
    for (Syntax.SwitchCase switchCase : switchExpression.cases()) {
      if (!definitelyYields(switchCase.body())) {
        diagnostics.error(
            INVALID_CONTROL, "switch expression case must produce a value", switchCase.span());
      }
    }
    if (result.isReference()) {
      LexicalLifetime lifetime =
          context.referenceLifetime() == null
              ? LexicalLifetime.unusable()
              : context.referenceLifetime();
      LexicalLifetime useLifetime = flowScopes.currentLifetime();
      if (!lifetime.outlives(useLifetime)) {
        diagnostics.error(
            INVALID_CONTROL,
            "reference cannot outlive the addressed storage location",
            switchExpression.span());
        lifetime = useLifetime;
      }
      referenceLifetimes.put(switchExpression.span(), lifetime);
    }
    return result;
  }

  PatternCoverage.Pattern analyzePattern(Syntax.Pattern pattern, SemanticType expected) {
    if (expected.isNullable() && !(pattern instanceof Syntax.NullPattern)) {
      if (pattern instanceof Syntax.WildcardPattern) return PatternCoverage.Pattern.any();
      return PatternCoverage.Pattern.constructor(
          "$value", List.of(analyzeNonNullPattern(pattern, expected.nonNullable())));
    }
    if (pattern instanceof Syntax.NullPattern) {
      if (!expected.isNullable()) {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return analyzeNonNullPattern(pattern, expected.nonNullable());
  }

  PatternCoverage.Pattern analyzeNonNullPattern(Syntax.Pattern pattern, SemanticType expected) {
    return switch (pattern) {
      case Syntax.WildcardPattern ignored -> PatternCoverage.Pattern.any();
      case Syntax.BindingPattern binding -> {
        validateType(binding.type(), false);
        SemanticType type = resolveType(binding.type(), activeTypeParameters);
        if (!type.equals(expected)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "pattern type " + type.displayName() + " does not match " + expected.displayName(),
              binding.type().span());
        }
        Symbol symbol =
            register(
                binding,
                binding.name(),
                SymbolKind.LOCAL_VARIABLE,
                type,
                binding.nameSpan(),
                currentCallable,
                List.of(),
                List.of());
        declareExisting(binding.name(), type, binding.nameSpan(), symbol.id());
        if (!lambdaLocals.isEmpty()) lambdaLocals.getFirst().add(symbol.id());
        yield PatternCoverage.Pattern.any();
      }
      case Syntax.VariantPattern variant -> analyzeVariantPattern(variant, expected);
      case Syntax.IntegerPattern integer -> {
        SemanticType literalType = numericIntegerType(integer.value(), expected, integer.span());
        requireType(expected, literalType, integer.span());
        semanticTypes.put(integer.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? integer.value()
                    : NumericTypes.materialize(integer.value(), literalType)),
            List.of());
      }
      case Syntax.DecimalPattern decimal -> {
        SemanticType literalType = numericDecimalType(decimal.value(), expected, decimal.span());
        requireType(expected, literalType, decimal.span());
        semanticTypes.put(decimal.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? decimal.value()
                    : NumericTypes.materialize(decimal.value(), literalType)),
            List.of());
      }
      case Syntax.CodePointPattern codePoint -> {
        requireType(SemanticType.CODE_POINT, expected, codePoint.span());
        yield PatternCoverage.Pattern.constructor("codepoint:" + codePoint.value(), List.of());
      }
      case Syntax.BooleanPattern bool -> {
        requireType(SemanticType.BOOLEAN, expected, bool.span());
        yield PatternCoverage.Pattern.constructor("boolean:" + bool.value(), List.of());
      }
      case Syntax.StringPattern string -> {
        requireType(SemanticType.STRING, expected, string.span());
        yield PatternCoverage.Pattern.constructor("string:" + string.value(), List.of());
      }
      case Syntax.NullPattern ignored -> {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
        yield PatternCoverage.Pattern.constructor("$null", List.of());
      }
    };
  }

  PatternCoverage.Pattern analyzeVariantPattern(
      Syntax.VariantPattern pattern, SemanticType expected) {
    Syntax.EnumDecl enumDecl = resolveEnum(expected);
    if (enumDecl == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern requires an enum value, found " + expected.displayName(),
          pattern.span());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    Syntax.EnumVariant variant =
        enumDecl.variants().stream()
            .filter(candidate -> candidate.name().equals(pattern.name()))
            .findFirst()
            .orElse(null);
    if (variant == null) {
      diagnostics.error(
          UNKNOWN_NAME,
          "enum '" + enumDecl.name() + "' has no variant '" + pattern.name() + "'",
          pattern.nameSpan());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    bindings.put(pattern.nameSpan(), declarationSymbols.get(variant));
    Map<String, SemanticType> substitutions = enumSubstitutions(enumDecl, expected);
    List<SemanticType> payloadTypes =
        variant.parameters().stream()
            .map(
                parameter ->
                    resolveDeclarationType(
                            parameter.type(), parameter, enumTypeParameters(enumDecl))
                        .substitute(substitutions))
            .toList();
    if (pattern.arguments().size() != payloadTypes.size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern '"
              + pattern.name()
              + "' requires "
              + payloadTypes.size()
              + " argument(s), found "
              + pattern.arguments().size(),
          pattern.span());
    }
    List<PatternCoverage.Pattern> arguments = new ArrayList<>();
    for (int index = 0;
        index < Math.min(pattern.arguments().size(), payloadTypes.size());
        index++) {
      arguments.add(analyzePattern(pattern.arguments().get(index), payloadTypes.get(index)));
    }
    return PatternCoverage.Pattern.constructor("variant:" + variant.name(), arguments);
  }

  void analyzeBreak(Syntax.BreakStatement statement) {
    if (controls.isEmpty()) {
      diagnostics.error(
          INVALID_CONTROL, "break is only valid inside for or switch", statement.span());
      if (statement.value() != null) typeOf(statement.value(), null);
      return;
    }
    ControlContext context = controls.getFirst();
    if (context.kind() != ControlKind.SWITCH) {
      if (statement.value() != null) {
        diagnostics.error(INVALID_CONTROL, "loop break cannot produce a value", statement.span());
        typeOf(statement.value(), null);
      }
      return;
    }
    if (statement.value() == null) {
      diagnostics.error(INVALID_CONTROL, "switch break must produce a value", statement.span());
      return;
    }
    SemanticType actual = typeOf(statement.value(), context.resultType());
    if (actual.isReference()) {
      context.mergeReferenceLifetime(referenceLifetime(statement.value()));
    }
    if (context.resultType() == null || context.resultType().equals(SemanticType.DYNAMIC)) {
      context.setResultType(actual);
    } else {
      requireAssignable(context.resultType(), actual, statement.value().span());
    }
  }

  TypeProbe probeType(Syntax.Expression expression, SemanticType expected) {
    AnalysisCheckpoint checkpoint = checkpoint();
    SemanticType type = typeOf(expression, expected);
    boolean hasErrors = diagnostics.hasErrorsSince(checkpoint.diagnosticMark());
    restore(checkpoint);
    return new TypeProbe(type, hasErrors);
  }

  AnalysisCheckpoint checkpoint() {
    return new AnalysisCheckpoint(
        Map.copyOf(bindings),
        Map.copyOf(semanticTypes),
        Map.copyOf(resolvedCalls),
        Map.copyOf(functionReferenceTypeArguments),
        Map.copyOf(iterations),
        Map.copyOf(indexes),
        Map.copyOf(referenceLifetimes),
        flowScopes.snapshot(),
        flowScopes.semanticScopeCount(),
        diagnostics.mark());
  }

  void restore(AnalysisCheckpoint checkpoint) {
    restore(bindings, checkpoint.bindings());
    restore(semanticTypes, checkpoint.semanticTypes());
    restore(resolvedCalls, checkpoint.resolvedCalls());
    restore(functionReferenceTypeArguments, checkpoint.functionReferenceTypeArguments());
    restore(iterations, checkpoint.iterations());
    restore(indexes, checkpoint.indexes());
    restore(referenceLifetimes, checkpoint.referenceLifetimes());
    flowScopes.replace(checkpoint.flowState());
    flowScopes.restoreSemanticScopes(checkpoint.semanticScopeCount());
    diagnostics.rollback(checkpoint.diagnosticMark());
  }

  static <K, V> void restore(Map<K, V> target, Map<K, V> snapshot) {
    target.clear();
    target.putAll(snapshot);
  }

  SemanticType analyzeNull(Syntax.NullLiteral literal, SemanticType expected) {
    if (expected == null || expected.equals(SemanticType.DYNAMIC)) {
      diagnostics.error(UNTYPED_NULL, "null requires an expected nullable type", literal.span());
      return SemanticType.DYNAMIC;
    }
    if (!expected.isNullable()) {
      diagnostics.error(
          NULLABILITY_MISMATCH,
          "null is not assignable to " + expected.displayName(),
          literal.span());
      return SemanticType.DYNAMIC;
    }
    return expected;
  }

  SemanticType analyzeArray(Syntax.ArrayLiteral array, SemanticType expected) {
    SemanticType expectedArray =
        expected == null
            ? null
            : builtins.resolveCollectionLiteral(expected).map(value -> value.type()).orElse(null);
    SemanticType expectedElement =
        expectedArray != null && expectedArray.arguments().size() == 1
            ? expectedArray.arguments().getFirst()
            : null;
    SemanticType elementType = expectedElement;
    for (Syntax.Expression element : array.elements()) {
      SemanticType current = typeOf(element, expectedElement);
      if (elementType == null && !containsDynamic(current)) {
        elementType = current;
      } else if (elementType != null && !containsDynamic(current)) {
        if (expectedElement != null) {
          if (!isAssignable(elementType, current)) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          }
        } else {
          SemanticType common = commonType(elementType, current).orElse(null);
          if (common == null) {
            diagnostics.error(
                TYPE_MISMATCH,
                "array elements must have one invariant type; found "
                    + elementType.displayName()
                    + " and "
                    + current.displayName(),
                element.span());
          } else {
            elementType = common;
          }
        }
      }
    }
    SemanticType inferredElement = elementType == null ? SemanticType.DYNAMIC : elementType;
    if (inferredElement.containsReference()) {
      diagnostics.error(TYPE_MISMATCH, "collection element type cannot contain ref", array.span());
      return SemanticType.DYNAMIC;
    }
    return expectedArray == null
        ? builtins.instantiate("Array", List.of(inferredElement))
        : expectedArray;
  }

  SemanticType analyzeUnary(Syntax.Unary unary, SemanticType expected) {
    if (unary.operator() == TokenKind.AMPERSAND) return analyzeAddress(unary);
    if (unary.operator() == TokenKind.STAR) {
      SemanticType operand = typeOf(unary.operand(), null);
      if (!operand.isReference()) {
        diagnostics.error(TYPE_MISMATCH, "dereference requires ref<T>", unary.span());
        return SemanticType.DYNAMIC;
      }
      return operand.referenceTarget();
    }
    SemanticType required =
        unary.operator() == TokenKind.BANG
            ? SemanticType.BOOLEAN
            : NumericTypes.isLeaf(expected == null ? SemanticType.DYNAMIC : expected)
                ? expected.nonNullable()
                : null;
    SemanticType operand = typeOf(unary.operand(), required);
    if (unary.operator() == TokenKind.BANG) {
      requireType(SemanticType.BOOLEAN, operand, unary.span());
      return SemanticType.BOOLEAN;
    }
    if (!NumericTypes.isLeaf(operand)) {
      diagnostics.error(TYPE_MISMATCH, "numeric negation requires a numeric leaf", unary.span());
      return SemanticType.DYNAMIC;
    }
    return operand;
  }

  SemanticType analyzeAddress(Syntax.Unary unary) {
    Syntax.Expression target = unary.operand();
    SemanticType targetType = typeOf(target, null);
    boolean addressable = false;
    if (target instanceof Syntax.Name name) {
      FlowScopes.ScopedSymbol scoped = findScoped(name.value());
      if (scoped != null) {
        SymbolKind kind = scopedSymbol(scoped).kind();
        addressable =
            (kind == SymbolKind.LOCAL_VARIABLE || kind == SymbolKind.PARAMETER)
                    && (lambdaLocals.isEmpty() || lambdaLocals.getFirst().contains(scoped.id()))
                || kind == SymbolKind.FIELD
                    && currentAggregate != null
                    && currentAggregate.kind() == Syntax.AggregateKind.CLASS;
      }
    } else if (target instanceof Syntax.Member member && !member.nullSafe()) {
      SemanticType receiver = semanticTypes.get(member.receiver().span());
      SymbolId fieldId = bindings.get(member.nameSpan());
      Symbol field = fieldId == null ? null : symbols.get(fieldId);
      addressable =
          receiver != null
              && receiver.nonNullable().category() == ValueCategory.IDENTITY
              && field != null
              && field.kind() == SymbolKind.FIELD;
    }
    if (!addressable) {
      diagnostics.error(
          TYPE_MISMATCH, "address-of requires a writable storage location", target.span());
      return SemanticType.DYNAMIC;
    }
    if (targetType.category() != ValueCategory.VALUE) {
      diagnostics.error(TYPE_MISMATCH, "ref target must be a value type", target.span());
      return SemanticType.DYNAMIC;
    }
    return SemanticType.reference(targetType);
  }

  SemanticType analyzeBinary(Syntax.Binary binary, SemanticType expected) {
    if (binary.operator() == TokenKind.QUESTION_QUESTION) {
      SemanticType leftExpected =
          expected == null || expected.isReference() ? expected : expected.nullable();
      SemanticType left = typeOf(binary.left(), leftExpected);
      if (!left.mayContainNull()) {
        diagnostics.error(TYPE_MISMATCH, "left side of ?? must be nullable", binary.left().span());
      }
      SemanticType result = left.equals(SemanticType.DYNAMIC) ? expected : left.nonNullable();
      SemanticType right = typeOf(binary.right(), result);
      if (result == null) return right;
      requireAssignable(result, right, binary.right().span());
      return result;
    }
    SemanticType left;
    SemanticType right;
    if ((binary.operator() == TokenKind.EQUAL_EQUAL || binary.operator() == TokenKind.BANG_EQUAL)
        && binary.left() instanceof Syntax.NullLiteral) {
      right = typeOf(binary.right(), null);
      left = typeOf(binary.left(), right);
    } else {
      SemanticType numericExpected =
          expected != null && NumericTypes.isLeaf(expected) ? expected.nonNullable() : null;
      left = typeOf(binary.left(), numericExpected);
      right = null;
    }
    if (right == null) {
      if (binary.operator() == TokenKind.AND_AND) {
        FlowScopes.FlowState incoming = flowScopes.snapshot();
        pushScope(binary.right().span());
        applyNarrowings(narrowingsFor(binary.left(), true));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        popScope();
        replaceFlow(incoming);
      } else if (binary.operator() == TokenKind.OR_OR) {
        FlowScopes.FlowState incoming = flowScopes.snapshot();
        pushScope(binary.right().span());
        applyNarrowings(narrowingsFor(binary.left(), false));
        right = typeOf(binary.right(), SemanticType.BOOLEAN);
        popScope();
        replaceFlow(incoming);
      } else {
        right = typeOf(binary.right(), left);
      }
    }
    return switch (binary.operator()) {
      case PLUS -> {
        if (left.equals(SemanticType.STRING) && right.equals(SemanticType.STRING)) {
          yield SemanticType.STRING;
        }
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case MINUS, STAR, SLASH, PERCENT -> {
        yield requireNumericLeaves(left, right, binary.span()) ? left : SemanticType.DYNAMIC;
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        requireNumericLeaves(left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case AND_AND, OR_OR -> {
        requireBoth(SemanticType.BOOLEAN, left, right, binary.span());
        yield SemanticType.BOOLEAN;
      }
      case EQUAL_EQUAL, BANG_EQUAL -> {
        if (!isAssignable(left, right) && !isAssignable(right, left)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "cannot compare " + left.displayName() + " with " + right.displayName(),
              binary.span());
        }
        yield SemanticType.BOOLEAN;
      }
      default -> SemanticType.DYNAMIC;
    };
  }

  SemanticType numericIntegerType(
      java.math.BigInteger value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.integerLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  SemanticType numericDecimalType(
      java.math.BigDecimal value, SemanticType expected, SourceSpan span) {
    try {
      return NumericTypes.decimalLiteralType(value, expected);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      diagnostics.error(TYPE_MISMATCH, exception.getMessage(), span);
      return SemanticType.DYNAMIC;
    }
  }

  boolean requireNumericLeaves(SemanticType left, SemanticType right, SourceSpan span) {
    if (NumericTypes.isLeaf(left) && left.equals(right)) return true;
    diagnostics.error(
        TYPE_MISMATCH,
        "numeric operands require the same concrete leaf type; found "
            + left.displayName()
            + " and "
            + right.displayName(),
        span);
    return false;
  }

  SemanticType analyzeCall(Syntax.Call call, SemanticType expected) {
    if (call.callee() instanceof Syntax.Name name) {
      return analyzeNamedCall(name, call, expected);
    }
    if (call.callee() instanceof Syntax.Member member) {
      if (member.receiver() instanceof Syntax.Name receiverName
          && (resolveEnum(receiverName.value()) != null
              || !builtins.typeMembers(receiverName.value(), member.name()).isEmpty())) {
        return analyzeMethodCall(member, call, expected, null);
      }
      SemanticType nullableReceiver = typeOf(member.receiver(), null);
      SemanticType memberType = memberTypeWithoutDiagnostics(member, nullableReceiver);
      if (memberType != null && memberType.isFunction()) {
        semanticTypes.put(member.span(), memberType);
        return analyzeFunctionInvocation(call, memberType, currentCallable);
      }
      return analyzeMethodCall(member, call, expected, nullableReceiver);
    }
    SemanticType calleeType = typeOf(call.callee(), null);
    if (calleeType.isFunction())
      return analyzeFunctionInvocation(call, calleeType, currentCallable);
    diagnostics.error(INVALID_CALL, "expression is not callable", call.callee().span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType analyzeFunctionInvocation(Syntax.Call call, SemanticType function, SymbolId target) {
    List<ParameterInfo> parameters =
        java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
            .mapToObj(
                index ->
                    new ParameterInfo(
                        "argument" + index, function.functionParameterTypes().get(index)))
            .toList();
    return recordCall(
        call,
        call.callee().span(),
        ResolvedCall.Kind.INVOKE,
        target,
        parameters,
        List.of(),
        function.functionReturnType());
  }

  SemanticType memberTypeWithoutDiagnostics(Syntax.Member member, SemanticType nullableReceiver) {
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    Syntax.AggregateDecl owner = resolveAggregate(receiver);
    if (owner == null) return null;
    AggregateField resolved = aggregateField(receiver, member.name());
    if (resolved == null) return null;
    Syntax.FieldDecl field = resolved.field();
    owner = resolved.view().declaration();
    if (field.visibility() == Syntax.Visibility.PRIVATE && currentAggregate != owner) {
      diagnostics.error(
          UNKNOWN_NAME,
          "field '"
              + member.name()
              + "' is private in "
              + aggregateKeyword(owner)
              + " '"
              + owner.name()
              + "'",
          member.nameSpan());
    }
    bindings.put(member.nameSpan(), declarationSymbols.get(field));
    SemanticType type =
        resolveDeclarationType(field.type(), field, aggregateTypeParameters(owner))
            .substitute(aggregateSubstitutions(owner, resolved.view().type()));
    return safeAccessResult(member, nullableReceiver, type);
  }

  SemanticType analyzeNamedCall(Syntax.Name name, Syntax.Call call, SemanticType expected) {
    String callee = name.value();
    FlowScopes.ScopedSymbol scoped = findScoped(callee);
    if (scoped != null && scoped.declaredType().isFunction()) {
      SemanticType function = scoped.declaredType();
      bindings.put(name.span(), scoped.id());
      semanticTypes.put(name.span(), function);
      List<ParameterInfo> parameters =
          java.util.stream.IntStream.range(0, function.functionParameterTypes().size())
              .mapToObj(
                  index ->
                      new ParameterInfo(
                          "argument" + index, function.functionParameterTypes().get(index)))
              .toList();
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INVOKE,
          scoped.id(),
          parameters,
          List.of(),
          function.functionReturnType());
    }
    builtins.type(callee).ifPresent(symbol -> bindings.put(name.span(), symbol.id()));
    List<Symbol> builtinFunctions = builtins.globals(callee, currentProgram.span().source().id());
    if (!builtinFunctions.isEmpty()) {
      if (name.diamond()) {
        diagnostics.error(
            INVALID_CALL, "diamond is only valid for generic constructors", name.span());
      }
      Symbol symbol = selectBuiltinOverload(builtinFunctions, call, name.span());
      if (symbol == null) return SemanticType.DYNAMIC;
      symbols.putIfAbsent(symbol.id(), symbol);
      bindings.put(name.span(), symbol.id());
      validateTypeArgumentCount(callee, 0, name.typeArguments(), name.span());
      name.typeArguments().forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          symbol.parameters(),
          List.of(),
          symbol.type());
    }
    SemanticType constructedType = constructedType(name, call, expected);
    Optional<List<ParameterInfo>> constructor = builtins.constructorParameters(constructedType);
    if (constructor.isPresent()) {
      Symbol target = builtins.type(callee).orElseThrow();
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.INTRINSIC,
          target.id(),
          constructor.orElseThrow(),
          List.of(),
          constructedType);
    }
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(callee);
    if (aggregateDecl != null) {
      bindDeclarationUse(name.span(), callee, aggregateDecl);
      Map<String, SemanticType> substitutions =
          aggregateSubstitutions(aggregateDecl, constructedType);
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CONSTRUCT,
          declarationSymbols.get(aggregateDecl),
          aggregateDecl.constructors().isEmpty()
              ? fieldParameters(aggregateDecl, substitutions)
              : parameters(
                  aggregateDecl.constructors().getFirst().parameters(),
                  substitutions,
                  aggregateTypeParameters(aggregateDecl)),
          List.of(),
          constructedType);
    }
    List<Syntax.FunctionDecl> functionCandidates = resolveFunctions(callee);
    if (!functionCandidates.isEmpty() && name.diamond()) {
      diagnostics.error(
          INVALID_CALL, "diamond is only valid for generic constructors", name.span());
    }
    SourceCallResolution resolution =
        resolveSourceCall(
            functionCandidates,
            name.typeArguments(),
            call,
            expected,
            Map.of(),
            name.span(),
            "function");
    if (resolution != null) {
      bindDeclarationUse(name.span(), callee, resolution.declaration());
      return recordCall(
          call,
          name.span(),
          ResolvedCall.Kind.CALLABLE,
          declarationSymbols.get(resolution.declaration()),
          resolution.parameters(),
          resolution.reifiedArguments(),
          resolution.result());
    }
    if (!functionCandidates.isEmpty()) return SemanticType.DYNAMIC;
    diagnostics.error(UNKNOWN_NAME, "cannot find function or type '" + callee + "'", name.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  SemanticType analyzeMethodCall(
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected,
      SemanticType analyzedReceiver) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = resolveEnum(enumName.value());
      if (enumDecl != null) {
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant == null) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no variant '" + member.name() + "'",
              member.nameSpan());
          analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        Symbol variantSymbol = symbols.get(declarationSymbols.get(variant));
        bindings.put(enumName.span(), declarationSymbols.get(enumDecl));
        bindings.put(member.nameSpan(), variantSymbol.id());
        Map<String, SemanticType> substitutions =
            inferBuiltinTypeArguments(
                variantSymbol, enumName.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            variantSymbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = variantSymbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            variantSymbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.ENUM_CONSTRUCT,
            variantSymbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    if (member.receiver() instanceof Syntax.Name typeName) {
      List<Symbol> typeMethods = builtins.typeMembers(typeName.value(), member.name());
      if (!typeMethods.isEmpty()) {
        builtins
            .type(typeName.value())
            .ifPresent(symbol -> bindings.put(typeName.span(), symbol.id()));
        Symbol symbol = selectBuiltinOverload(typeMethods, call, member.nameSpan());
        if (symbol == null) return SemanticType.DYNAMIC;
        bindings.put(member.nameSpan(), symbol.id());
        Map<String, SemanticType> substitutions =
            inferBuiltinTypeArguments(
                symbol, member.typeArguments(), call, expected, member.span());
        List<ParameterInfo> parameters =
            symbol.parameters().stream()
                .map(
                    parameter ->
                        new ParameterInfo(
                            parameter.name(), parameter.type().substitute(substitutions)))
                .toList();
        SemanticType result = symbol.type().substitute(substitutions);
        List<SemanticType> reifiedArguments =
            symbol.typeParameters().stream()
                .map(TypeParameterInfo::type)
                .map(parameter -> substitutions.get(parameter.identity()))
                .toList();
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.INTRINSIC,
            symbol.id(),
            parameters,
            reifiedArguments,
            result);
      }
    }
    SemanticType nullableReceiver =
        analyzedReceiver == null ? typeOf(member.receiver(), null) : analyzedReceiver;
    SemanticType receiver = accessibleReceiverType(member, nullableReceiver);
    if (member.name().isEmpty()) {
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    List<Symbol> builtinMembers = builtins.members(receiver, member.name());
    if (!builtinMembers.isEmpty()) {
      List<Symbol> builtinMethods =
          builtinMembers.stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD).toList();
      if (builtinMethods.isEmpty()) {
        diagnostics.error(
            UNKNOWN_NAME,
            "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
            call.span());
        analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
      Symbol symbol = selectBuiltinOverload(builtinMethods, call, member.nameSpan());
      if (symbol == null) return SemanticType.DYNAMIC;
      bindings.put(member.nameSpan(), symbol.id());
      validateTypeArgumentCount(member.name(), 0, member.typeArguments(), member.span());
      member
          .typeArguments()
          .forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
      return recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTRINSIC,
          symbol.id(),
          symbol.parameters(),
          List.of(),
          safeAccessResult(member, nullableReceiver, symbol.type()));
    }
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(receiver);
    if (aggregateDecl != null) {
      if (aggregateDecl.kind() == Syntax.AggregateKind.CLASS && member.name().equals("copy")) {
        bindings.put(member.nameSpan(), copyMethods.get(receiver.identity()));
        validateTypeArgumentCount(member.name(), 0, member.typeArguments(), member.span());
        member
            .typeArguments()
            .forEach(argument -> resolveCheckedType(argument, activeTypeParameters));
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.COPY,
            copyMethods.get(receiver.identity()),
            List.of(),
            List.of(),
            safeAccessResult(member, nullableReceiver, receiver));
      }
      boolean foundMethod = false;
      boolean foundAccessibleMethod = false;
      for (AggregateView view : aggregateViews(receiver)) {
        List<Syntax.FunctionDecl> methods =
            view.declaration().methods().stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .toList();
        foundMethod |= !methods.isEmpty();
        List<Syntax.FunctionDecl> accessibleMethods =
            methods.stream()
                .filter(
                    candidate ->
                        candidate.visibility() != Syntax.Visibility.PRIVATE
                            || currentAggregate == view.declaration())
                .toList();
        foundAccessibleMethod |= !accessibleMethods.isEmpty();
        Map<String, SemanticType> substitutions =
            aggregateSubstitutions(view.declaration(), view.type());
        boolean structuralMatch =
            accessibleMethods.stream()
                .anyMatch(
                    candidate ->
                        overloads.argumentIndices(
                                call, parametersOf(candidate, substitutions), false)
                            != null);
        if (!structuralMatch) continue;
        SourceCallResolution resolution =
            resolveSourceCall(
                accessibleMethods,
                member.typeArguments(),
                call,
                callableExpected(member, nullableReceiver, expected),
                substitutions,
                member.nameSpan(),
                "method");
        if (resolution == null) return SemanticType.DYNAMIC;
        Syntax.FunctionDecl method = resolution.declaration();
        bindings.put(member.nameSpan(), declarationSymbols.get(method));
        return recordCall(
            call,
            member.nameSpan(),
            ResolvedCall.Kind.CALLABLE,
            declarationSymbols.get(method),
            resolution.parameters(),
            resolution.reifiedArguments(),
            safeAccessResult(member, nullableReceiver, resolution.result()));
      }
      if (foundMethod) {
        if (!foundAccessibleMethod) {
          diagnostics.error(
              UNKNOWN_NAME,
              "method '"
                  + member.name()
                  + "' is private in "
                  + aggregateKeyword(aggregateDecl)
                  + " '"
                  + aggregateDecl.name()
                  + "'",
              member.nameSpan());
          analyzeArguments(call.arguments());
          return SemanticType.DYNAMIC;
        }
        diagnostics.error(
            INVALID_CALL, "no method overload accepts the supplied arguments", call.span());
        analyzeArguments(call.arguments());
        return SemanticType.DYNAMIC;
      }
    }
    List<InterfaceRequirement> interfaceMethods =
        interfaceRequirements(receiver).stream()
            .filter(requirement -> requirement.method().name().equals(member.name()))
            .toList();
    OverloadResolver.Candidate selectedInterfaceMethod =
        overloads.select(
            interfaceMethods.stream()
                .map(
                    requirement ->
                        new OverloadResolver.Candidate(requirement, requirement.parameters()))
                .toList(),
            call,
            member.nameSpan());
    InterfaceRequirement interfaceMethod =
        selectedInterfaceMethod == null
            ? null
            : (InterfaceRequirement) selectedInterfaceMethod.target();
    if (!interfaceMethods.isEmpty() && interfaceMethod == null) return SemanticType.DYNAMIC;
    if (interfaceMethod != null) {
      Symbol target = symbols.get(declarationSymbols.get(interfaceMethod.method()));
      InterfaceCallResolution interfaceResolution =
          resolveInterfaceCall(interfaceMethod, target, member, call, expected);
      bindings.put(member.nameSpan(), target.id());
      return recordCall(
          call,
          member.nameSpan(),
          ResolvedCall.Kind.INTERFACE_CALL,
          target.id(),
          interfaceResolution.parameters(),
          interfaceResolution.reifiedArguments(),
          safeAccessResult(member, nullableReceiver, interfaceResolution.result()));
    }
    if (builtins.isType(receiver.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiver.displayName() + "' has no method '" + member.name() + "'",
          member.span());
      analyzeArguments(call.arguments());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH, "type '" + receiver.displayName() + "' has no methods", member.span());
    analyzeArguments(call.arguments());
    return SemanticType.DYNAMIC;
  }

  InterfaceCallResolution resolveInterfaceCall(
      InterfaceRequirement requirement,
      Symbol method,
      Syntax.Member member,
      Syntax.Call call,
      SemanticType expected) {
    Map<String, SemanticType> substitutions =
        new LinkedHashMap<>(interfaceSubstitutions(requirement.owner(), requirement.receiver()));
    List<TypeConstraintSolver.Conflict> inferenceConflicts = List.of();
    List<SemanticType> explicit =
        member.typeArguments().stream()
            .map(argument -> resolveCheckedType(argument, activeTypeParameters))
            .toList();
    if (!explicit.isEmpty()) {
      validateTypeArgumentCount(
          member.name(), method.typeParameters().size(), member.typeArguments(), member.span());
      for (int index = 0;
          index < Math.min(explicit.size(), method.typeParameters().size());
          index++) {
        substitutions.put(
            method.typeParameters().get(index).type().identity(), explicit.get(index));
      }
    } else {
      TypeConstraintSolver solver =
          new TypeConstraintSolver(
              method.typeParameters().stream().map(TypeParameterInfo::type).toList());
      Set<String> variables = solverVariables(method.typeParameters());
      if (expected != null && !expected.equals(SemanticType.DYNAMIC)) {
        constrainInference(solver, requirement.result().substitute(substitutions), expected);
      }
      Map<String, SemanticType> contextualSubstitutions = new LinkedHashMap<>(substitutions);
      contextualSubstitutions.putAll(solver.solve().substitutions());
      List<Integer> indices = overloads.argumentIndices(call, requirement.parameters(), false);
      if (indices != null) {
        for (int index = 0; index < call.arguments().size(); index++) {
          Syntax.Expression argument = call.arguments().get(index).value();
          SemanticType inferencePattern =
              requirement.parameters().get(indices.get(index)).type().substitute(substitutions);
          SemanticType pattern = inferencePattern.substitute(contextualSubstitutions);
          SemanticType argumentExpected =
              containsTypeParameter(pattern, variables)
                      && !(argument instanceof Syntax.Lambda && pattern.isFunction())
                  ? null
                  : pattern;
          constrainInference(solver, inferencePattern, typeOf(argument, argumentExpected));
        }
      }
      TypeConstraintSolver.Solution inferred = solver.solve();
      substitutions.putAll(inferred.substitutions());
      inferenceConflicts = inferred.conflicts();
    }
    List<SemanticType> reified = new ArrayList<>();
    for (TypeParameterInfo parameter : method.typeParameters()) {
      SemanticType argument = substitutions.get(parameter.type().identity());
      if (argument == null) {
        diagnostics.error(
            INVALID_CALL, "cannot infer type argument '" + parameter.name() + "'", member.span());
        argument = SemanticType.DYNAMIC;
        substitutions.put(parameter.type().identity(), argument);
      }
      SemanticType bound =
          parameter.upperBound().map(value -> value.substitute(substitutions)).orElse(null);
      if (bound != null && !isAssignable(bound, argument)) {
        diagnostics.error(
            TYPE_MISMATCH,
            "type argument '"
                + argument.displayName()
                + "' does not satisfy bound '"
                + bound.displayName()
                + "' for '"
                + parameter.name()
                + "'",
            member.span());
      }
      reified.add(argument);
    }
    Map<String, String> parameterNames =
        method.typeParameters().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    parameter -> parameter.type().identity(),
                    TypeParameterInfo::name,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (TypeConstraintSolver.Conflict conflict : inferenceConflicts) {
      diagnostics.error(
          TYPE_MISMATCH,
          "type parameter '"
              + parameterNames.get(conflict.variable())
              + "' inferred as both "
              + conflict.first().displayName()
              + " and "
              + conflict.second().displayName(),
          member.span());
    }
    List<ParameterInfo> parameters =
        requirement.parameters().stream()
            .map(value -> new ParameterInfo(value.name(), value.type().substitute(substitutions)))
            .toList();
    return new InterfaceCallResolution(
        parameters, requirement.result().substitute(substitutions), reified);
  }

  SemanticType memberType(Syntax.Member member) {
    if (member.receiver() instanceof Syntax.Name enumName) {
      Syntax.EnumDecl enumDecl = resolveEnum(enumName.value());
      if (enumDecl != null) {
        bindings.put(enumName.span(), declarationSymbols.get(enumDecl));
        enumDecl.variants().stream()
            .filter(value -> value.name().equals(member.name()))
            .findFirst()
            .map(declarationSymbols::get)
            .ifPresent(id -> bindings.put(member.nameSpan(), id));
        if (enumDecl.variants().stream().noneMatch(value -> value.name().equals(member.name()))) {
          diagnostics.error(
              UNKNOWN_NAME,
              "enum '" + enumDecl.name() + "' has no member '" + member.name() + "'",
              member.span());
        }
        Syntax.EnumVariant variant =
            enumDecl.variants().stream()
                .filter(value -> value.name().equals(member.name()))
                .findFirst()
                .orElse(null);
        if (variant != null && !variant.parameters().isEmpty()) {
          diagnostics.error(
              INVALID_CALL,
              "enum variant '" + member.name() + "' requires construction arguments",
              member.span());
        }
        return appliedType(enumDecl.name(), enumName.typeArguments(), enumName.span());
      }
    }
    SemanticType nullableReceiverType = typeOf(member.receiver(), null);
    SemanticType receiverType = accessibleReceiverType(member, nullableReceiverType);
    if (member.name().isEmpty()) return SemanticType.DYNAMIC;
    Optional<Symbol> builtinMember = builtins.member(receiverType, member.name());
    if (builtinMember.isPresent() && builtinMember.orElseThrow().kind() != SymbolKind.METHOD) {
      Symbol symbol = builtinMember.orElseThrow();
      bindings.put(member.nameSpan(), symbol.id());
      return safeAccessResult(member, nullableReceiverType, symbol.type());
    }
    if (builtins.isType(receiverType.name())) {
      diagnostics.error(
          UNKNOWN_NAME,
          "type '" + receiverType.displayName() + "' has no field '" + member.name() + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    Syntax.AggregateDecl aggregateDecl = resolveAggregate(receiverType);
    if (aggregateDecl != null) {
      AggregateField resolved = aggregateField(receiverType, member.name());
      if (resolved != null) {
        Syntax.FieldDecl field = resolved.field();
        Syntax.AggregateDecl owner = resolved.view().declaration();
        if (field.visibility() == Syntax.Visibility.PRIVATE && currentAggregate != owner) {
          diagnostics.error(
              UNKNOWN_NAME,
              "field '"
                  + member.name()
                  + "' is private in "
                  + aggregateKeyword(owner)
                  + " '"
                  + owner.name()
                  + "'",
              member.nameSpan());
        }
        bindings.put(member.nameSpan(), declarationSymbols.get(field));
        SemanticType result =
            resolveDeclarationType(field.type(), field, aggregateTypeParameters(owner))
                .substitute(aggregateSubstitutions(owner, resolved.view().type()));
        return safeAccessResult(member, nullableReceiverType, result);
      }
      diagnostics.error(
          UNKNOWN_NAME,
          aggregateKeyword(aggregateDecl)
              + " '"
              + receiverType.displayName()
              + "' has no field '"
              + member.name()
              + "'",
          member.span());
      return SemanticType.DYNAMIC;
    }
    diagnostics.error(
        TYPE_MISMATCH,
        "type '" + receiverType.displayName() + "' has no member '" + member.name() + "'",
        member.span());
    return SemanticType.DYNAMIC;
  }

  List<InterfaceRequirement> interfaceRequirements(SemanticType receiver) {
    SemanticType interfaceType = receiver;
    if (receiver.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      interfaceType = typeParameterBounds.get(receiver.identity());
    }
    if (interfaceType == null) return List.of();
    Syntax.InterfaceDecl root = resolveInterface(interfaceType);
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    if (root != null) {
      collectConformances(root, interfaceType, conformances, currentProgram.span());
    } else {
      for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
        String identity = symbols.get(declarationSymbols.get(declaration)).type().identity();
        conformanceTo(interfaceType, identity)
            .ifPresent(value -> conformances.putIfAbsent(value.identity(), value));
      }
    }
    if (conformances.isEmpty()) return List.of();
    Map<String, InterfaceRequirement> result = new LinkedHashMap<>();
    for (SemanticType conformance : conformances.values()) {
      Syntax.InterfaceDecl declaration = resolveInterface(conformance);
      if (declaration == null) continue;
      directRequirements(declaration, conformance)
          .forEach(requirement -> result.putIfAbsent(requirement.key(), requirement));
    }
    return List.copyOf(result.values());
  }

  Optional<ResolvedIteration> resolveInterfaceIteration(SemanticType iterableType) {
    if (builtins.resolveIterable(iterableType).isPresent()) return Optional.empty();
    SemanticType iterableInterface = conformanceTo(iterableType, "std.core.Iterable").orElse(null);
    if (iterableInterface == null || iterableInterface.arguments().size() != 1) {
      return Optional.empty();
    }
    InterfaceRequirement iterator =
        interfaceRequirements(iterableInterface).stream()
            .filter(requirement -> requirement.method().name().equals("iterator"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (iterator == null) return Optional.empty();
    SemanticType iteratorInterface = iterator.result();
    InterfaceRequirement hasNext =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("hasNext"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    InterfaceRequirement next =
        interfaceRequirements(iteratorInterface).stream()
            .filter(requirement -> requirement.method().name().equals("next"))
            .filter(requirement -> requirement.parameters().isEmpty())
            .findFirst()
            .orElse(null);
    if (hasNext == null || next == null || !hasNext.result().equals(SemanticType.BOOLEAN)) {
      return Optional.empty();
    }
    return Optional.of(
        new ResolvedIteration(
            iterableInterface.arguments().getFirst(),
            new ResolvedIteration.Strategy.Interface(
                iterableInterface,
                declarationSymbols.get(iterator.method()),
                iteratorInterface,
                declarationSymbols.get(hasNext.method()),
                declarationSymbols.get(next.method()))));
  }

  Optional<SemanticType> conformanceTo(SemanticType concrete, String interfaceIdentity) {
    if (concrete.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = typeParameterBounds.get(concrete.identity());
      if (bound == null) return Optional.empty();
      return conformanceTo(bound, interfaceIdentity);
    }
    Syntax.InterfaceDecl directInterface = interfaceByIdentity(concrete.identity());
    if (directInterface != null) {
      Map<String, SemanticType> conformances = new LinkedHashMap<>();
      collectConformances(directInterface, concrete, conformances, currentProgram.span());
      return Optional.ofNullable(conformances.get(interfaceIdentity));
    }
    Optional<SemanticType> builtinConformance =
        builtins.protocolConformances(concrete).stream()
            .filter(value -> value.identity().equals(interfaceIdentity))
            .findFirst();
    if (builtinConformance.isPresent()) return builtinConformance;
    if (resolveAggregate(concrete) == null) return Optional.empty();
    Syntax.Program previous = currentProgram;
    Map<String, SemanticType> conformances = new LinkedHashMap<>();
    for (AggregateView view : aggregateViews(concrete)) {
      currentProgram = declarations.owner(view.declaration());
      Map<String, SemanticType> parameters = aggregateTypeParameters(view.declaration());
      Map<String, SemanticType> substitutions =
          aggregateSubstitutions(view.declaration(), view.type());
      for (Syntax.TypeRef interfaceRef : view.declaration().implementedInterfaces()) {
        SemanticType conformance = resolveType(interfaceRef, parameters).substitute(substitutions);
        Syntax.InterfaceDecl contract = resolveInterface(conformance);
        if (contract != null) {
          collectConformances(contract, conformance, conformances, interfaceRef.span());
        }
      }
    }
    currentProgram = previous;
    return Optional.ofNullable(conformances.get(interfaceIdentity));
  }

  Syntax.InterfaceDecl interfaceByIdentity(String identity) {
    for (Syntax.InterfaceDecl declaration : declarations.interfaces()) {
      Syntax.Program owner = declarations.owner(declaration);
      String candidate = qualifiedName(owner.packageName(), declaration.name());
      if (declaration.visibility() == Syntax.Visibility.PRIVATE) {
        candidate = fileLocalIdentity(candidate, owner);
      }
      if (candidate.equals(identity)) return declaration;
    }
    return null;
  }

  SemanticType accessibleReceiverType(Syntax.Member member, SemanticType receiverType) {
    if (receiverType.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      SemanticType bound = typeParameterBounds.get(receiverType.identity());
      if (bound != null && !bound.mayContainNull()) return receiverType;
    }
    if (!receiverType.mayContainNull()) return receiverType;
    if (!member.nullSafe()) {
      diagnostics.error(
          UNSAFE_NULLABLE_ACCESS,
          "nullable value of type "
              + receiverType.displayName()
              + " must be narrowed or accessed with ?.",
          member.receiver().span());
    }
    return receiverType.equals(SemanticType.DYNAMIC)
        ? SemanticType.DYNAMIC
        : receiverType.nonNullable();
  }

  static SemanticType safeAccessResult(
      Syntax.Member member, SemanticType receiverType, SemanticType result) {
    if (!member.nullSafe()
        || !receiverType.mayContainNull()
        || result.kind() == SemanticType.Kind.VOID
        || result.equals(SemanticType.DYNAMIC)) {
      return result;
    }
    return result.nullable();
  }

  static SemanticType callableExpected(
      Syntax.Member member, SemanticType receiverType, SemanticType expected) {
    if (expected != null
        && member.nullSafe()
        && receiverType.mayContainNull()
        && expected.isNullable()) {
      return expected.nonNullable();
    }
    return expected;
  }

  SemanticType analyzeIndex(Syntax.Index index) {
    SemanticType receiverType = typeOf(index.receiver(), null);
    SemanticType indexType = typeOf(index.index(), null);
    Optional<dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex> resolved =
        builtins.resolveIndex(receiverType);
    if (resolved.isEmpty()) {
      diagnostics.error(TYPE_MISMATCH, "only Array, List, and Map can be indexed", index.span());
      return SemanticType.DYNAMIC;
    }
    dev.w0fv1.norm.builtin.BuiltinCatalog.ResolvedIndex capability = resolved.orElseThrow();
    indexes.put(
        index.span(),
        new ResolvedIndex(
            capability.kind(),
            capability.keyType(),
            capability.resultType(),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
    requireType(capability.keyType(), indexType, index.index().span());
    return capability.resultType();
  }

  SemanticType assignmentTargetType(Syntax.Expression target) {
    return switch (target) {
      case Syntax.Name name -> lookupDeclared(name.value(), name.span());
      case Syntax.Member member -> {
        if (member.nullSafe()) {
          diagnostics.error(TYPE_MISMATCH, "safe access cannot be assigned", member.span());
        }
        SemanticType receiver = typeOf(member.receiver(), null);
        if (receiver.nonNullable().category() == dev.w0fv1.norm.semantic.ValueCategory.VALUE
            && resolveAggregate(receiver.nonNullable()) != null) {
          diagnostics.error(TYPE_MISMATCH, "value field cannot be assigned", member.span());
        }
        yield memberType(member);
      }
      case Syntax.Index index -> analyzeIndex(index);
      case Syntax.Unary unary when unary.operator() == TokenKind.STAR -> {
        SemanticType reference = typeOf(unary.operand(), null);
        if (!reference.isReference()) {
          diagnostics.error(TYPE_MISMATCH, "dereference assignment requires ref<T>", unary.span());
          yield SemanticType.DYNAMIC;
        }
        semanticTypes.put(unary.span(), reference.referenceTarget());
        yield reference.referenceTarget();
      }
      default -> {
        diagnostics.error(TYPE_MISMATCH, "invalid assignment target", target.span());
        yield SemanticType.DYNAMIC;
      }
    };
  }

  static boolean definitelyYields(List<Syntax.Statement> statements) {
    for (Syntax.Statement statement : statements) {
      if (statement instanceof Syntax.ReturnStatement
          || statement instanceof Syntax.ThrowStatement
          || statement instanceof Syntax.BreakStatement broken && broken.value() != null) {
        return true;
      }
      if (statement instanceof Syntax.IfStatement conditional
          && definitelyYields(conditional.thenBody())
          && definitelyYields(conditional.elseBody())) {
        return true;
      }
      if (statement instanceof Syntax.TryStatement tried) {
        if (tried.finallyClause().isPresent()
            && definitelyYields(tried.finallyClause().orElseThrow().body())) {
          return true;
        }
        if (definitelyYields(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyYields(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  final class SemanticPatternDomain implements PatternCoverage.Domain<SemanticType> {
    @Override
    public List<PatternCoverage.Constructor<SemanticType>> constructors(SemanticType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>("$value", List.of(type.nonNullable())));
      }
      if (type.equals(SemanticType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      Syntax.EnumDecl declaration = resolveEnum(type);
      if (declaration == null) return List.of();
      Map<String, SemanticType> substitutions = enumSubstitutions(declaration, type);
      Map<String, SemanticType> parameters = enumTypeParameters(declaration);
      return declaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.name(),
                      variant.parameters().stream()
                          .map(
                              field ->
                                  resolveDeclarationType(field.type(), field, parameters)
                                      .substitute(substitutions))
                          .toList()))
          .toList();
    }

    @Override
    public PatternCoverage.Constructor<SemanticType> openConstructor(
        SemanticType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }
}
